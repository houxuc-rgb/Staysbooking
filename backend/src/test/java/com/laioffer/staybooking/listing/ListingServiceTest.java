package com.laioffer.staybooking.listing;

import com.laioffer.staybooking.booking.BookingService;
import com.laioffer.staybooking.location.GeocodingService;
import com.laioffer.staybooking.model.GeoPoint;
import com.laioffer.staybooking.model.ListingDto;
import com.laioffer.staybooking.model.ListingEntity;
import com.laioffer.staybooking.model.UserEntity;
import com.laioffer.staybooking.model.UserRole;
import com.laioffer.staybooking.repository.ListingRepository;
import com.laioffer.staybooking.storage.ImageStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingServiceTest {

    @Mock
    private BookingService bookingService;

    @Mock
    private GeocodingService geocodingService;

    @Mock
    private ImageStorageService imageStorageService;

    @Mock
    private ListingRepository listingRepository;

    @InjectMocks
    private ListingService listingService;

    @Test
    void getListingsReturnsListingsForHost() {
        ListingEntity listing = listingEntity(11L, 7L);
        when(listingRepository.findAllByHostId(7L)).thenReturn(List.of(listing));

        List<ListingDto> listings = listingService.getListings(7L);

        assertThat(listings).hasSize(1);
        assertThat(listings.get(0).id()).isEqualTo(11L);
        assertThat(listings.get(0).host().id()).isEqualTo(7L);
        assertThat(listings.get(0).location()).isEqualTo(new GeoPoint(37.8, -122.4));
    }

    @Test
    void createListingUploadsNonEmptyImagesAndSavesListing() {
        MockMultipartFile frontImage = new MockMultipartFile(
                "images",
                "front.jpg",
                "image/jpeg",
                "front".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile emptyImage = new MockMultipartFile(
                "images",
                "empty.jpg",
                "image/jpeg",
                new byte[0]
        );
        MockMultipartFile bedroomImage = new MockMultipartFile(
                "images",
                "bedroom.jpg",
                "image/jpeg",
                "bedroom".getBytes(StandardCharsets.UTF_8)
        );
        when(imageStorageService.upload(frontImage)).thenReturn("https://storage/front.jpg");
        when(imageStorageService.upload(bedroomImage)).thenReturn("https://storage/bedroom.jpg");
        when(geocodingService.getGeoPoint("1 Market St")).thenReturn(new GeoPoint(37.0, -122.0));

        listingService.createListing(
                7L,
                "Sunny room",
                "1 Market St",
                "A clean room near transit",
                2,
                List.of(frontImage, emptyImage, bedroomImage)
        );

        ArgumentCaptor<ListingEntity> listingCaptor = ArgumentCaptor.forClass(ListingEntity.class);
        verify(listingRepository).save(listingCaptor.capture());
        ListingEntity savedListing = listingCaptor.getValue();
        assertThat(savedListing.getId()).isNull();
        assertThat(savedListing.getHostId()).isEqualTo(7L);
        assertThat(savedListing.getName()).isEqualTo("Sunny room");
        assertThat(savedListing.getAddress()).isEqualTo("1 Market St");
        assertThat(savedListing.getDescription()).isEqualTo("A clean room near transit");
        assertThat(savedListing.getGuestNumber()).isEqualTo(2);
        assertThat(savedListing.getImageUrls()).containsExactlyInAnyOrder(
                "https://storage/front.jpg",
                "https://storage/bedroom.jpg"
        );
        assertThat(savedListing.getLocation().getCoordinate().x).isEqualTo(-122.0);
        assertThat(savedListing.getLocation().getCoordinate().y).isEqualTo(37.0);
        verify(imageStorageService, never()).upload(emptyImage);
    }

    @Test
    void deleteListingDeletesHostOwnedListingWithoutActiveBookings() {
        when(listingRepository.getReferenceById(11L)).thenReturn(listingEntity(11L, 7L));
        when(bookingService.existsActiveBookings(11L)).thenReturn(false);

        listingService.deleteListing(7L, 11L);

        verify(listingRepository).deleteById(11L);
    }

    @Test
    void deleteListingRejectsNonOwner() {
        when(listingRepository.getReferenceById(11L)).thenReturn(listingEntity(11L, 8L));

        assertThatThrownBy(() -> listingService.deleteListing(7L, 11L))
                .isInstanceOf(DeleteListingNotAllowedException.class)
                .hasMessageContaining("Host 7 not allowed to delete listing 11");

        verifyNoInteractions(bookingService);
        verify(listingRepository, never()).deleteById(anyLong());
    }

    @Test
    void deleteListingRejectsListingWithActiveBookings() {
        when(listingRepository.getReferenceById(11L)).thenReturn(listingEntity(11L, 7L));
        when(bookingService.existsActiveBookings(11L)).thenReturn(true);

        assertThatThrownBy(() -> listingService.deleteListing(7L, 11L))
                .isInstanceOf(DeleteListingNotAllowedException.class)
                .hasMessageContaining("Active bookings exist");

        verify(listingRepository, never()).deleteById(anyLong());
    }

    @Test
    void searchDelegatesValidSearchToRepository() {
        LocalDate checkIn = LocalDate.now().plusDays(1);
        LocalDate checkOut = checkIn.plusDays(2);
        ListingEntity listing = listingEntity(11L, 7L);
        when(listingRepository.searchListings(37.8, -122.4, 1000, checkIn, checkOut, 2))
                .thenReturn(List.of(listing));

        List<ListingDto> listings = listingService.search(37.8, -122.4, 1000, checkIn, checkOut, 2);

        assertThat(listings).hasSize(1);
        assertThat(listings.get(0).id()).isEqualTo(11L);
        verify(listingRepository).searchListings(37.8, -122.4, 1000, checkIn, checkOut, 2);
    }

    @Test
    void searchRejectsInvalidSearchCriteria() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDate dayAfterTomorrow = tomorrow.plusDays(1);

        assertThatThrownBy(() -> listingService.search(91.0, -122.4, 1000, tomorrow, dayAfterTomorrow, 2))
                .isInstanceOf(InvalidListingSearchException.class)
                .hasMessage("Invalid latitude or longitude.");
        assertThatThrownBy(() -> listingService.search(37.8, -181.0, 1000, tomorrow, dayAfterTomorrow, 2))
                .isInstanceOf(InvalidListingSearchException.class)
                .hasMessage("Invalid latitude or longitude.");
        assertThatThrownBy(() -> listingService.search(37.8, -122.4, 0, tomorrow, dayAfterTomorrow, 2))
                .isInstanceOf(InvalidListingSearchException.class)
                .hasMessage("Distance must be positive.");
        assertThatThrownBy(() -> listingService.search(37.8, -122.4, 1000, dayAfterTomorrow, tomorrow, 2))
                .isInstanceOf(InvalidListingSearchException.class)
                .hasMessage("Check-in date must be before check-out date.");
        assertThatThrownBy(() -> listingService.search(37.8, -122.4, 1000, LocalDate.now().minusDays(1), tomorrow, 2))
                .isInstanceOf(InvalidListingSearchException.class)
                .hasMessage("Check-in date must be in the future.");

        verifyNoInteractions(listingRepository);
    }

    private static ListingEntity listingEntity(long listingId, long hostId) {
        ListingEntity listing = new ListingEntity(
                listingId,
                hostId,
                "Listing " + listingId,
                "Address " + listingId,
                "Description " + listingId,
                4,
                List.of("https://storage/listing-" + listingId + ".jpg"),
                new GeometryFactory().createPoint(new Coordinate(-122.4, 37.8))
        );
        ReflectionTestUtils.setField(
                listing,
                "host",
                new UserEntity(hostId, "host-" + hostId, "password", UserRole.ROLE_HOST)
        );
        return listing;
    }
}

package com.laioffer.staybooking.booking;

import com.laioffer.staybooking.model.BookingDto;
import com.laioffer.staybooking.model.BookingEntity;
import com.laioffer.staybooking.model.ListingEntity;
import com.laioffer.staybooking.model.UserEntity;
import com.laioffer.staybooking.model.UserRole;
import com.laioffer.staybooking.repository.BookingRepository;
import com.laioffer.staybooking.repository.ListingRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private ListingRepository listingRepository;

    @InjectMocks
    private BookingService bookingService;

    @Test
    void findBookingsByGuestIdReturnsGuestBookings() {
        LocalDate checkIn = LocalDate.now().plusDays(1);
        LocalDate checkOut = checkIn.plusDays(2);
        BookingEntity booking = bookingEntity(21L, 9L, listingEntity(11L, 7L), checkIn, checkOut);
        when(bookingRepository.findAllByGuestId(9L)).thenReturn(List.of(booking));

        List<BookingDto> bookings = bookingService.findBookingsByGuestId(9L);

        assertThat(bookings).hasSize(1);
        assertThat(bookings.get(0).id()).isEqualTo(21L);
        assertThat(bookings.get(0).guest().id()).isEqualTo(9L);
        assertThat(bookings.get(0).listing().id()).isEqualTo(11L);
        assertThat(bookings.get(0).checkInDate()).isEqualTo(checkIn);
        assertThat(bookings.get(0).checkOutDate()).isEqualTo(checkOut);
    }

    @Test
    void findBookingsByListingIdReturnsBookingsForListingOwner() {
        LocalDate checkIn = LocalDate.now().plusDays(1);
        LocalDate checkOut = checkIn.plusDays(2);
        ListingEntity listing = listingEntity(11L, 7L);
        BookingEntity booking = bookingEntity(21L, 9L, listing, checkIn, checkOut);
        when(listingRepository.getReferenceById(11L)).thenReturn(listing);
        when(bookingRepository.findAllByListingId(11L)).thenReturn(List.of(booking));

        List<BookingDto> bookings = bookingService.findBookingsByListingId(7L, 11L);

        assertThat(bookings).hasSize(1);
        assertThat(bookings.get(0).id()).isEqualTo(21L);
        assertThat(bookings.get(0).listing().host().id()).isEqualTo(7L);
    }

    @Test
    void findBookingsByListingIdRejectsNonOwner() {
        when(listingRepository.getReferenceById(11L)).thenReturn(listingEntity(11L, 8L));

        assertThatThrownBy(() -> bookingService.findBookingsByListingId(7L, 11L))
                .isInstanceOf(ListingBookingsNotAllowedException.class)
                .hasMessage("Host 7 not allowed to get bookings of listing 11");

        verifyNoInteractions(bookingRepository);
    }

    @Test
    void createBookingSavesBookingWhenListingExistsAndDatesAreAvailable() {
        LocalDate checkIn = LocalDate.now().plusDays(1);
        LocalDate checkOut = checkIn.plusDays(2);
        when(listingRepository.existsById(11L)).thenReturn(true);
        when(bookingRepository.findOverlappedBookings(11L, checkIn, checkOut)).thenReturn(List.of());

        bookingService.createBooking(9L, 11L, checkIn, checkOut);

        ArgumentCaptor<BookingEntity> bookingCaptor = ArgumentCaptor.forClass(BookingEntity.class);
        verify(bookingRepository).save(bookingCaptor.capture());
        BookingEntity savedBooking = bookingCaptor.getValue();
        assertThat(savedBooking.getId()).isNull();
        assertThat(savedBooking.getGuestId()).isEqualTo(9L);
        assertThat(savedBooking.getListingId()).isEqualTo(11L);
        assertThat(savedBooking.getCheckInDate()).isEqualTo(checkIn);
        assertThat(savedBooking.getCheckOutDate()).isEqualTo(checkOut);
    }

    @Test
    void createBookingRejectsMissingListing() {
        LocalDate checkIn = LocalDate.now().plusDays(1);
        LocalDate checkOut = checkIn.plusDays(2);
        when(listingRepository.existsById(11L)).thenReturn(false);

        assertThatThrownBy(() -> bookingService.createBooking(9L, 11L, checkIn, checkOut))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Listing 11 not found");

        verify(bookingRepository, never()).findOverlappedBookings(anyLong(), any(LocalDate.class), any(LocalDate.class));
        verify(bookingRepository, never()).save(any(BookingEntity.class));
    }

    @Test
    void createBookingRejectsCheckInAfterCheckOut() {
        LocalDate checkOut = LocalDate.now().plusDays(1);
        LocalDate checkIn = checkOut.plusDays(1);
        when(listingRepository.existsById(11L)).thenReturn(true);

        assertThatThrownBy(() -> bookingService.createBooking(9L, 11L, checkIn, checkOut))
                .isInstanceOf(InvalidBookingException.class)
                .hasMessage("Check-in date must be before check-out date.");

        verify(bookingRepository, never()).findOverlappedBookings(anyLong(), any(LocalDate.class), any(LocalDate.class));
        verify(bookingRepository, never()).save(any(BookingEntity.class));
    }

    @Test
    void createBookingRejectsPastCheckInDate() {
        LocalDate checkIn = LocalDate.now().minusDays(1);
        LocalDate checkOut = LocalDate.now().plusDays(1);
        when(listingRepository.existsById(11L)).thenReturn(true);

        assertThatThrownBy(() -> bookingService.createBooking(9L, 11L, checkIn, checkOut))
                .isInstanceOf(InvalidBookingException.class)
                .hasMessage("Check-in date must be in the future.");

        verify(bookingRepository, never()).findOverlappedBookings(anyLong(), any(LocalDate.class), any(LocalDate.class));
        verify(bookingRepository, never()).save(any(BookingEntity.class));
    }

    @Test
    void createBookingRejectsOverlappedBooking() {
        LocalDate checkIn = LocalDate.now().plusDays(1);
        LocalDate checkOut = checkIn.plusDays(2);
        when(listingRepository.existsById(11L)).thenReturn(true);
        when(bookingRepository.findOverlappedBookings(11L, checkIn, checkOut))
                .thenReturn(List.of(new BookingEntity(21L, 10L, 11L, checkIn, checkOut)));

        assertThatThrownBy(() -> bookingService.createBooking(9L, 11L, checkIn, checkOut))
                .isInstanceOf(InvalidBookingException.class)
                .hasMessage("Booking dates conflict, please select different dates.");

        verify(bookingRepository, never()).save(any(BookingEntity.class));
    }

    @Test
    void deleteBookingDeletesGuestOwnedBooking() {
        LocalDate checkIn = LocalDate.now().plusDays(1);
        LocalDate checkOut = checkIn.plusDays(2);
        when(bookingRepository.getReferenceById(21L))
                .thenReturn(new BookingEntity(21L, 9L, 11L, checkIn, checkOut));

        bookingService.deleteBooking(9L, 21L);

        verify(bookingRepository).deleteById(21L);
    }

    @Test
    void deleteBookingRejectsNonOwner() {
        LocalDate checkIn = LocalDate.now().plusDays(1);
        LocalDate checkOut = checkIn.plusDays(2);
        when(bookingRepository.getReferenceById(21L))
                .thenReturn(new BookingEntity(21L, 10L, 11L, checkIn, checkOut));

        assertThatThrownBy(() -> bookingService.deleteBooking(9L, 21L))
                .isInstanceOf(DeleteBookingNotAllowedException.class)
                .hasMessage("Guest 9 not allow to delete the booking 21");

        verify(bookingRepository, never()).deleteById(anyLong());
    }

    @Test
    void existsActiveBookingsChecksBookingsThatEndAfterToday() {
        when(bookingRepository.existsByListingIdAndCheckOutDateAfter(eq(11L), any(LocalDate.class))).thenReturn(true);

        boolean exists = bookingService.existsActiveBookings(11L);

        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(bookingRepository).existsByListingIdAndCheckOutDateAfter(eq(11L), dateCaptor.capture());
        assertThat(exists).isTrue();
        assertThat(dateCaptor.getValue()).isEqualTo(LocalDate.now());
    }

    private static BookingEntity bookingEntity(
            long bookingId,
            long guestId,
            ListingEntity listing,
            LocalDate checkIn,
            LocalDate checkOut
    ) {
        BookingEntity booking = new BookingEntity(bookingId, guestId, listing.getId(), checkIn, checkOut);
        ReflectionTestUtils.setField(
                booking,
                "guest",
                new UserEntity(guestId, "guest-" + guestId, "password", UserRole.ROLE_GUEST)
        );
        ReflectionTestUtils.setField(booking, "listing", listing);
        return booking;
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

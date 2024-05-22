package roomescape.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import roomescape.TestFixture;
import roomescape.domain.member.Role;
import roomescape.domain.reservation.Reservation;
import roomescape.domain.reservation.ReservationStatus;
import roomescape.domain.theme.Theme;
import roomescape.dto.auth.LoginMember;
import roomescape.dto.reservation.MyReservationWithRankResponse;
import roomescape.dto.reservation.ReservationFilterParam;
import roomescape.dto.reservation.ReservationResponse;
import roomescape.dto.reservation.ReservationTimeResponse;
import roomescape.dto.theme.ReservedThemeResponse;
import roomescape.repository.ReservationRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;
import static roomescape.TestFixture.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @InjectMocks
    private ReservationService reservationService;

    @Test
    @DisplayName("예약을 생성한다.")
    void create() {
        // given
        final Reservation reservation = new Reservation(TestFixture.MEMBER_MIA(), DATE_MAY_EIGHTH,
                RESERVATION_TIME_SIX(), THEME_HORROR(), ReservationStatus.RESERVED);
        given(reservationRepository.save(reservation))
                .willReturn(new Reservation(1L, reservation.getMember(), reservation.getDate(),
                        reservation.getTime(), reservation.getTheme(), ReservationStatus.RESERVED));

        // when
        final ReservationResponse response = reservationService.create(reservation);

        // then
        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("동일한 테마, 날짜, 시간에 예약이 초과된 경우 예외가 발생한다.")
    void throwExceptionWhenCreateDuplicatedReservation() {
        // given
        final Theme theme = THEME_HORROR(1L);
        final Reservation reservation = new Reservation(MEMBER_MIA(), DATE_MAY_EIGHTH,
                RESERVATION_TIME_SIX(), theme, ReservationStatus.RESERVED);
        given(reservationRepository.countByDateAndTimeIdAndThemeId(LocalDate.parse(DATE_MAY_EIGHTH),
                RESERVATION_TIME_SIX().getId(), theme.getId()))
                .willReturn(1);

        // when & then
        assertThatThrownBy(() -> reservationService.create(reservation))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("모든 예약 목록을 조회한다.")
    void findAllReservations() {
        // given
        final Reservation reservation1 = new Reservation(TestFixture.MEMBER_MIA(), DATE_MAY_EIGHTH,
                RESERVATION_TIME_SIX(), THEME_HORROR(), ReservationStatus.RESERVED);
        final Reservation reservation2 = new Reservation(ADMIN(), DATE_MAY_EIGHTH,
                RESERVATION_TIME_SEVEN(), THEME_DETECTIVE(), ReservationStatus.RESERVED);
        given(reservationRepository.findAll())
                .willReturn(List.of(reservation1, reservation2));

        // when
        final List<ReservationResponse> reservations = reservationService.findAll();

        // then
        assertAll(() -> {
            assertThat(reservations).hasSize(2)
                    .extracting(ReservationResponse::name)
                    .containsExactly(TestFixture.MEMBER_MIA_NAME, ADMIN_NAME);
            assertThat(reservations).extracting(ReservationResponse::date)
                    .containsExactly(LocalDate.parse(DATE_MAY_EIGHTH), LocalDate.parse(DATE_MAY_EIGHTH));
            assertThat(reservations).extracting(ReservationResponse::time)
                    .extracting(ReservationTimeResponse::startAt)
                    .containsExactly(START_AT_SIX, START_AT_SEVEN);
            assertThat(reservations).extracting(ReservationResponse::theme)
                    .extracting(ReservedThemeResponse::name)
                    .containsExactly(THEME_HORROR_NAME, THEME_DETECTIVE_NAME);
        });
    }

    @Test
    @DisplayName("검색 조건에 따른 예약 목록을 조회한다.")
    void findAllByFilterParameter() {
        // given
        final Reservation reservation1 = new Reservation(TestFixture.MEMBER_MIA(), DATE_MAY_EIGHTH,
                RESERVATION_TIME_SIX(), THEME_HORROR(), ReservationStatus.RESERVED);
        final Reservation reservation2 = new Reservation(TestFixture.MEMBER_MIA(), DATE_MAY_NINTH,
                RESERVATION_TIME_SIX(), THEME_HORROR(), ReservationStatus.RESERVED);
        final ReservationFilterParam reservationFilterParam
                = new ReservationFilterParam(1L, 1L,
                LocalDate.parse("2034-05-08"), LocalDate.parse("2034-05-28"));
        given(reservationRepository.findByThemeIdAndMemberIdAndDateBetweenAndStatus(1L, 1L,
                LocalDate.parse("2034-05-08"), LocalDate.parse("2034-05-28"), ReservationStatus.RESERVED))
                .willReturn(List.of(reservation1, reservation2));

        // when
        final List<ReservationResponse> reservations
                = reservationService.findAllBy(reservationFilterParam);

        // then
        assertAll(() -> {
            assertThat(reservations).hasSize(2)
                    .extracting(ReservationResponse::name)
                    .containsExactly(TestFixture.MEMBER_MIA_NAME, TestFixture.MEMBER_MIA_NAME);
            assertThat(reservations).extracting(ReservationResponse::time)
                    .extracting(ReservationTimeResponse::startAt)
                    .containsExactly(START_AT_SIX, START_AT_SIX);
            assertThat(reservations).extracting(ReservationResponse::theme)
                    .extracting(ReservedThemeResponse::name)
                    .containsExactly(THEME_HORROR_NAME, THEME_HORROR_NAME);
        });
    }

    @Test
    @DisplayName("예약을 삭제한다.")
    void delete() {
        // given
        final Long existingId = 1L;
        given(reservationRepository.existsById(existingId)).willReturn(true);

        // when & then
        assertThatCode(() -> reservationService.delete(existingId))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("삭제하려는 예약이 존재하지 않는 경우 예외가 발생한다.")
    void throwExceptionWhenDeleteNotExistingReservation() {
        // given
        final Long notExistingId = 1L;
        given(reservationRepository.existsById(notExistingId)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> reservationService.delete(notExistingId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("특정 사용자의 예약 목록을 조회한다.")
    void findMyReservations() {
        // given
        final LoginMember loginMember = new LoginMember(1L, MEMBER_MIA_NAME, MEMBER_MIA_EMAIL, Role.MEMBER);
        final Reservation reservation = new Reservation(TestFixture.MEMBER_MIA(), DATE_MAY_EIGHTH,
                RESERVATION_TIME_SIX(), THEME_HORROR(), ReservationStatus.WAITING);
        final MyReservationWithRankResponse response = new MyReservationWithRankResponse(reservation, 1L);
        given(reservationRepository.findByMemberId(loginMember.id()))
                .willReturn(List.of(response));

        // when
        final List<MyReservationWithRankResponse> actual = reservationService.findMyReservations(loginMember);

        // then
        assertAll(
                () -> assertThat(actual).hasSize(1),
                () -> assertThat(actual.get(0).getStatus()).isEqualTo(ReservationStatus.WAITING.getValue()),
                () -> assertThat(actual.get(0).getRank()).isEqualTo(1L)
        );
    }

    @Test
    @DisplayName("예약 대기 목록을 조회한다.")
    void findReservationWaitings() {
        // given
        final Reservation reservation = new Reservation(TestFixture.MEMBER_MIA(), DATE_MAY_EIGHTH,
                RESERVATION_TIME_SIX(), THEME_HORROR(), ReservationStatus.WAITING);
        given(reservationRepository.findByStatus(ReservationStatus.WAITING))
                .willReturn(List.of(reservation));

        // when
        final List<ReservationResponse> actual = reservationService.findReservationWaitings();

        // then
        assertThat(actual).hasSize(1);
    }

    @Test
    @DisplayName("예약 대기를 승인한다.")
    void approveReservationWaiting() {
        // given
        final Reservation waiting = new Reservation(1L, TestFixture.MEMBER_MIA(), DATE_MAY_EIGHTH,
                RESERVATION_TIME_SIX(), THEME_HORROR(), ReservationStatus.WAITING);
        given(reservationRepository.findById(waiting.getId())).willReturn(Optional.of(waiting));
        given(reservationRepository.existsByThemeAndDateAndTimeAndStatus(waiting.getTheme(), waiting.getDate(),
                waiting.getTime(), ReservationStatus.RESERVED))
                .willReturn(false);

        // when
        reservationService.approveReservationWaiting(waiting.getId());

        // then
        assertThat(waiting.getStatus()).isEqualTo(ReservationStatus.RESERVED);
    }

    @Test
    @DisplayName("이미 예약이 있는 상태에서 승인을 할 경우 예외가 발생한다.")
    void throwExceptionWhenAlreadyExistsReservation() {
        // given
        final Reservation waiting = new Reservation(1L, TestFixture.MEMBER_MIA(), DATE_MAY_EIGHTH,
                RESERVATION_TIME_SIX(), THEME_HORROR(), ReservationStatus.WAITING);
        given(reservationRepository.findById(waiting.getId())).willReturn(Optional.of(waiting));
        given(reservationRepository.existsByThemeAndDateAndTimeAndStatus(waiting.getTheme(), waiting.getDate(),
                waiting.getTime(), ReservationStatus.RESERVED))
                .willReturn(true);

        // when & then
        assertThatThrownBy(() -> reservationService.approveReservationWaiting(waiting.getId()))
                .isInstanceOf(IllegalStateException.class);
    }
}

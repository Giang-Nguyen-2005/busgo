package com.busgo.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class DemoSeatLayoutTest {
    @Test
    void representativeLayoutsHaveExactCountsUniqueCoordinatesAndMeaningfulFloors() {
        assertLayout(DemoDataSeeder.limousineSeats(), 22, 1);
        assertLayout(DemoDataSeeder.sleeperSeats(), 34, 2);
        assertLayout(DemoDataSeeder.standardSeats(), 40, 1);
    }

    private static void assertLayout(List<DemoDataSeeder.SeatSpec> seats, int count, int floors) {
        assertThat(seats).hasSize(count);
        assertThat(seats.stream().map(DemoDataSeeder.SeatSpec::code)).doesNotHaveDuplicates();
        assertThat(seats.stream().map(seat -> seat.floor() + ":" + seat.row() + ":" + seat.column()))
                .doesNotHaveDuplicates();
        assertThat(new HashSet<>(seats.stream().map(DemoDataSeeder.SeatSpec::floor).toList()))
                .hasSize(floors);
        assertThat(seats).allSatisfy(seat -> {
            assertThat(seat.row()).isPositive();
            assertThat(seat.column()).isPositive();
            assertThat(seat.floor()).isPositive();
        });
    }
}

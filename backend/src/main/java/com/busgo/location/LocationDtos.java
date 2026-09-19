package com.busgo.location;

public final class LocationDtos {
    private LocationDtos() {}

    public record LocationResponse(Long id, String name, String province, String district) {}
}

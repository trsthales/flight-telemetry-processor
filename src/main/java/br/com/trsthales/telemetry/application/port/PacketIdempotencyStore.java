package br.com.trsthales.telemetry.application.port;

public interface PacketIdempotencyStore {

    boolean tryAcquire(String packetId);

    void release(String packetId);
}
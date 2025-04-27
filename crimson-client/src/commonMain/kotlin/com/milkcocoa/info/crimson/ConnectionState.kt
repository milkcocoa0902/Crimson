package com.milkcocoa.info.crimson

/**
 * Connection state of crimson client.
 */
enum class ConnectionState{
    /**
     * Connection is closed.
     */
    CLOSED,
    /**
     * Connection is connecting.
     */
    CONNECTING,
    /**
     * Connection is established.
     */
    CONNECTED,
    /**
     * Connection is retrying.
     */
    RETRYING
}
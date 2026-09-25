# Change Log

## [0.0.16] - 2026-09-25

### added
- Splitted File Log - the file log now may be split into many files keeping the active log limited in records.

### fixed

- [@delocalizer](https://github.com/elixir-europe/beacon-network-backend/pull/14) fixed an issue with accidental erasing beaconInfoResults info metadata.

### improvements

- [@delocalizer](https://github.com/elixir-europe/beacon-network-backend/pull/13) moving the docker image to Java 21 and introducing HTTP/2 JDK variable 'JDK_HTTPCLIENT_KEEPALIVE_TIMEOUT_H2'.

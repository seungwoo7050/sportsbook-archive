# Sportsbook API Gateway

The gateway is the public HTTP and WebSocket boundary for the sportsbook platform. It will verify
user access tokens, apply distributed request limits, proxy approved API routes, and fan out live
updates from Kafka to STOMP clients.

## Contract boundary

The service is intended to expose only explicitly approved public routes. Internal service headers
are not part of the public contract and must never be trusted when supplied by a client.

The gateway consumes the shared protocol library for common value, error, and event contracts. It
does not own betting, wallet, risk, odds, or settlement state.

## Runtime baseline

The service targets Java 17 and is built with Maven. Runtime configuration is supplied through the
environment; credentials and private key material do not belong in the repository.

## Current status

This branch currently contains the project introduction only. Build configuration and runtime
behavior are added in subsequent development commits.

package com.nolanbarry.gateway.model

class InvalidDataException(message: String): RuntimeException(message)
class MisconfigurationException(message: String): RuntimeException(message)
class IncompatibleServerStateException(message: String): RuntimeException(message)
class UnrecoverableServerException(message: String): RuntimeException(message)

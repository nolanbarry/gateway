package com.nolanbarry.gateway.model

class IncompleteBufferException: RuntimeException("Buffer does not contain enough bytes to perform the operation")
class InvalidDataException(message: String): RuntimeException(message)
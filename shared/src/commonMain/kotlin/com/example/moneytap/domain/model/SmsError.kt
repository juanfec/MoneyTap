package com.example.moneytap.domain.model

sealed class SmsError {
    data object PermissionDenied : SmsError()
    data object PermissionPermanentlyDenied : SmsError()
    data object PlatformNotSupported : SmsError()
    data object EmptyInbox : SmsError()
    data class Unknown(val message: String) : SmsError()
}

class SmsException(val error: SmsError) : Exception(error.toString())

package com.digibuddy.shared.authentication

interface RefreshTokenStore {
    fun read(): String?

    fun save(refreshToken: String)

    fun clear()
}

class InMemoryRefreshTokenStore : RefreshTokenStore {
    private var value: String? = null

    override fun read(): String? = value

    override fun save(refreshToken: String) {
        value = refreshToken
    }

    override fun clear() {
        value = null
    }
}

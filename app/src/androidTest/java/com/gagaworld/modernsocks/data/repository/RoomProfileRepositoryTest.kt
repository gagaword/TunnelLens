package com.gagaworld.modernsocks.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gagaworld.modernsocks.data.local.ModernSocksDatabase
import com.gagaworld.modernsocks.data.model.ProfileDraft
import com.gagaworld.modernsocks.data.model.ProfileValidator
import com.gagaworld.modernsocks.security.AndroidKeystoreCredentialStore
import com.gagaworld.modernsocks.data.model.AppRoutingMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomProfileRepositoryTest {
    private lateinit var database: ModernSocksDatabase
    private lateinit var repository: RoomProfileRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ModernSocksDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomProfileRepository(
            database = database,
            credentialStore = AndroidKeystoreCredentialStore(),
            now = { 100L },
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun authenticatedProfileStoresOnlyEncryptedCredentialEnvelope() = runTest {
        val profile = checkNotNull(
            ProfileValidator.validate(
                ProfileDraft(
                    name = "Protected",
                    host = "example.invalid",
                    authenticationEnabled = true,
                    username = "private-user",
                    password = "private-password",
                ),
            ).profile,
        )

        val id = repository.save(profile)
        val entity = database.proxyProfileDao().getById(id)
        val editDraft = repository.getDraft(id)
        val connectionProfile = repository.getSelectedConnectionProfile()

        assertNotNull(entity?.credentialIv)
        assertNotNull(entity?.credentialCiphertext)
        assertFalse(entity!!.credentialCiphertext!!.toString(Charsets.UTF_8).contains("private-password"))
        assertEquals("private-user", editDraft?.username)
        assertEquals("", editDraft?.password)
        assertEquals(true, editDraft?.hasStoredPassword)
        assertEquals("private-user", connectionProfile?.credentials?.username)
        assertEquals("private-password", connectionProfile?.credentials?.password)
        assertEquals(true, connectionProfile?.bypassLan)
        assertFalse(connectionProfile.toString().contains("private-password"))
    }

    @Test
    fun createSelectDeleteAndUndo_preserveProfileState() = runTest {
        val firstId = repository.save(validProfile("First", "first.invalid"))
        val secondId = repository.save(validProfile("Second", "second.invalid"))

        assertEquals(firstId, repository.selectedProfile.first()?.id)
        repository.select(secondId)
        assertEquals(secondId, repository.selectedProfile.first()?.id)

        val deleted = repository.delete(secondId)
        assertNotNull(deleted)
        assertEquals(firstId, repository.selectedProfile.first()?.id)

        repository.restore(checkNotNull(deleted))
        assertEquals(secondId, repository.selectedProfile.first()?.id)
        assertEquals(2, repository.profiles.first().size)
    }

    @Test
    fun disablingAuthentication_removesCredentialEnvelope() = runTest {
        val id = repository.save(
            validProfile("Protected", "example.invalid", username = "user", password = "password"),
        )
        val edited = checkNotNull(repository.getDraft(id)).copy(
            authenticationEnabled = false,
            username = "",
            password = "",
        )
        repository.save(checkNotNull(ProfileValidator.validate(edited).profile))

        val entity = database.proxyProfileDao().getById(id)
        assertNull(entity?.credentialIv)
        assertNull(entity?.credentialCiphertext)
    }

    @Test
    fun appRoutingPersistsAndSurvivesOrdinaryProfileEdit() = runTest {
        val id = repository.save(validProfile("Apps", "apps.invalid"))
        repository.updateAppRouting(
            id,
            AppRoutingMode.ONLY_SELECTED,
            setOf("com.example.browser"),
        )
        val edited = checkNotNull(repository.getDraft(id)).copy(name = "Apps edited")
        repository.save(checkNotNull(ProfileValidator.validate(edited).profile))

        val policy = repository.getAppRouting(id)
        assertEquals(AppRoutingMode.ONLY_SELECTED, policy?.mode)
        assertEquals(setOf("com.example.browser"), policy?.packages)
        assertEquals(policy, repository.getSelectedConnectionProfile()?.appRoutingPolicy)
    }

    @Test
    fun connectionProfileCarriesDisabledLocalNetworkBypass() = runTest {
        repository.save(
            checkNotNull(
                ProfileValidator.validate(
                    ProfileDraft(
                        name = "Route all",
                        host = "route-all.invalid",
                        bypassLan = false,
                    ),
                ).profile,
            ),
        )

        assertFalse(checkNotNull(repository.getSelectedConnectionProfile()).bypassLan)
    }

    private fun validProfile(
        name: String,
        host: String,
        username: String = "",
        password: String = "",
    ) = checkNotNull(
        ProfileValidator.validate(
            ProfileDraft(
                name = name,
                host = host,
                authenticationEnabled = username.isNotEmpty(),
                username = username,
                password = password,
            ),
        ).profile,
    )
}

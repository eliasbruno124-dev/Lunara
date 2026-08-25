package de.lunara.app.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.lunara.app.core.security.KeystoreSecretStore
import de.lunara.app.core.security.DatabaseSecrets
import de.lunara.app.core.security.SecretStore
import de.lunara.app.data.local.LunaraDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.SecureRandom
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {
    @Binds
    @Singleton
    abstract fun bindSecretStore(implementation: KeystoreSecretStore): SecretStore
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun gson(): Gson = Gson()

    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
        secretStore: SecretStore,
    ): LunaraDatabase {
        System.loadLibrary("sqlcipher")
        val passphrase = secretStore.getBytes(DatabaseSecrets.PASSPHRASE_KEY) ?: ByteArray(32).also { generated ->
            SecureRandom().nextBytes(generated)
            secretStore.putBytes(DatabaseSecrets.PASSPHRASE_KEY, generated.copyOf())
        }
        val factory = SupportOpenHelperFactory(passphrase)
        return Room.databaseBuilder(context, LunaraDatabase::class.java, "lunara-encrypted.db")
            .openHelperFactory(factory)
            .addMigrations(LunaraDatabase.MIGRATION_1_2)
            .build()
    }
}

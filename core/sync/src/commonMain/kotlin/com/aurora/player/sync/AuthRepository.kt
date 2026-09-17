package com.aurora.player.sync

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * Konta użytkowników + sesja — wspólna warstwa dla telefonu (Android) i desktopu (Windows),
 * DESIGN.md Etap 36. Backend to Supabase (Postgres + Auth), wybrany zamiast Firebase: Firebase
 * nie ma oficjalnego klienta JVM/desktop (tylko REST bez cache offline, albo nieoficjalny port
 * w stanie alfa z Auth ograniczonym do email/hasła), a supabase-kt wspiera Android i JVM/Desktop
 * jako pełnoprawne targety z tej samej bazy kodu (`:core:sync` — jedyny prawdziwie
 * multiplatformowy moduł w projekcie, patrz build.gradle.kts).
 *
 * `Result<Unit>` zamiast rzucania — appka i tak musi pokazać błąd logowania w UI, więc
 * wymuszenie obsługi w miejscu wywołania (zamiast `try/catch` rozproszonego po ViewModelach)
 * jest tańsze niż osobna hierarchia wyjątków.
 */
interface AuthRepository {
    val sessionStatus: StateFlow<SessionStatus>
    val currentUserId: String?

    suspend fun signUp(email: String, password: String): Result<Unit>
    suspend fun signIn(email: String, password: String): Result<Unit>
    suspend fun signOut(): Result<Unit>
}

class SupabaseAuthRepository(private val client: SupabaseClient) : AuthRepository {

    override val sessionStatus: StateFlow<SessionStatus>
        get() = client.auth.sessionStatus

    override val currentUserId: String?
        get() = (sessionStatus.value as? SessionStatus.Authenticated)?.session?.user?.id

    override suspend fun signUp(email: String, password: String): Result<Unit> = runCatching {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        Unit
    }

    override suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        Unit
    }

    override suspend fun signOut(): Result<Unit> = runCatching {
        client.auth.signOut()
    }
}

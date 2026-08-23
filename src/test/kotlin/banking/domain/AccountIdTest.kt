package banking.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AccountIdTest {
    @Test
    fun generate_produces_unique_ids() {
        val ids = List(1_000) { AccountId.generate() }.toSet()
        assertEquals(1_000, ids.size)
    }

    @Test
    fun account_and_transaction_ids_with_the_same_uuid_are_not_interchangeable() {
        val uuid = AccountId.generate().value
        val accountId: Any = AccountId(uuid)
        val transactionId: Any = TransactionId(uuid)
        assertNotEquals(accountId, transactionId)
        assertEquals(AccountId::class, accountId::class)
        assertEquals(TransactionId::class, transactionId::class)
    }
}

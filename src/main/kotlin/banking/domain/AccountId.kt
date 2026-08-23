package banking.domain

import kotlin.uuid.Uuid

@JvmInline
value class AccountId(val value: Uuid) {
    companion object {
        fun generate(): AccountId = AccountId(Uuid.random())
    }
}

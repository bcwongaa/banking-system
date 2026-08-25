package banking.domain

import kotlin.uuid.Uuid

@JvmInline
value class TransactionId(val value: Uuid) {
    companion object {
        fun generate(): TransactionId = TransactionId(Uuid.random())
    }
}

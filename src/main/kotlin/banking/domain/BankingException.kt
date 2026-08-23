package banking.domain

sealed class BankingException(message: String) : RuntimeException(message)

class InvalidAmount(message: String = "Amount must be greater than zero") : BankingException(message)

class AccountNotFound(accountId: AccountId) :
    BankingException("Account ${accountId.value} was not found")

class InsufficientFunds : BankingException("Insufficient funds")

class CurrencyMismatch(message: String = "Currency does not match the account") : BankingException(message)

class SameAccountTransfer : BankingException("Cannot transfer to the same account")

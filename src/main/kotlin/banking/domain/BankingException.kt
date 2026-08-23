package banking.domain

sealed class BankingException(message: String) : RuntimeException(message)

class InvalidAmount : BankingException("Amount must be greater than zero")

class AccountNotFound(accountId: AccountId) :
    BankingException("Account ${accountId.value} was not found")

class InsufficientFunds : BankingException("Insufficient funds")

class CurrencyMismatch : BankingException("Currency does not match the account")

class SameAccountTransfer : BankingException("Cannot transfer to the same account")

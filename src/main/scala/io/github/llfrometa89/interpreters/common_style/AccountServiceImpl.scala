package io.github.llfrometa89.interpreters.common_style

import java.util.Date

import cats.Monad
import cats.effect.Sync
import cats.implicits._
import io.github.llfrometa89.domain.model._
import io.github.llfrometa89.domain.repositories.AccountRepository
import io.github.llfrometa89.domain.services.AccountMessage.{AlreadyExistAccount, NotFoundAccount}
import io.github.llfrometa89.domain.services.AccountService
import io.github.llfrometa89.implicits.syntax.all._
import monocle.std.option._

class AccountServiceImpl[F[_]: Sync](accountRepository: AccountRepository[F]) extends AccountService[F] {

  implicit val monad: Monad[F] = Sync[F]

  def open(no: String, name: String, rate: Option[BigDecimal], accountType: AccountType): F[Account] = {

    def validate: Either[Throwable, Account] = Account.validate(no, name, rate, accountType)

    def save: F[Account] =
      for {
        accountValidated <- Sync[F].pure(validate).rethrow
        account          <- accountRepository.save(accountValidated)
      } yield account

    for {
      maybeAccount <- accountRepository.findByNo(no)
      account      <- maybeAccount.mapOrElse(_ => Sync[F].raiseError(AlreadyExistAccount(no)), save)
    } yield account

  }

  def close(no: String, closeDate: Date): F[Account] = {

    def save(account: Account): F[Account] = {
      val accountUpdated = account match {
        case ca: CheckingAccount => (CheckingAccount.dateOfClose composePrism some).set(closeDate)(ca)
        case sa: SavingsAccount  => (SavingsAccount.dateOfClose composePrism some).set(closeDate)(sa)
      }
      accountRepository.save(accountUpdated)
    }

    for {
      maybeAccount <- accountRepository.findByNo(no)
      account      <- maybeAccount.mapOrElse(save, Sync[F].raiseError(NotFoundAccount(no)))
    } yield account
  }

  def debit(no: String, amount: Amount): F[Account] =
    for {
      maybeAccount        <- accountRepository.findByNo(no)
      maybeAccountUpdated <- maybeAccount.map(acc => updateBalance(acc, -amount)).pure[F]
      account             <- maybeAccountUpdated.mapOrElse(accountRepository.save, Sync[F].raiseError(NotFoundAccount(no)))
    } yield account

  def credit(no: String, amount: Amount): F[Account] =
    for {
      maybeAccount        <- accountRepository.findByNo(no)
      maybeAccountUpdated <- maybeAccount.map(acc => updateBalance(acc, amount)).pure[F]
      account             <- maybeAccountUpdated.mapOrElse(accountRepository.save, Sync[F].raiseError(NotFoundAccount(no)))
    } yield account

  private def updateBalance(account: Account, amount: Amount): Account = account match {
    case ca: CheckingAccount => CheckingAccount.balance.set(Balance(ca.balance.amount + amount))(ca)
    case sa: SavingsAccount  => SavingsAccount.balance.set(Balance(sa.balance.amount + amount))(sa)
  }
}

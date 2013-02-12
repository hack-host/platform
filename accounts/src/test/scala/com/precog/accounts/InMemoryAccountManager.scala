/*
 *  ____    ____    _____    ____    ___     ____ 
 * |  _ \  |  _ \  | ____|  / ___|  / _/    / ___|        Precog (R)
 * | |_) | | |_) | |  _|   | |     | |  /| | |  _         Advanced Analytics Engine for NoSQL Data
 * |  __/  |  _ <  | |___  | |___  |/ _| | | |_| |        Copyright (C) 2010 - 2013 SlamData, Inc.
 * |_|     |_| \_\ |_____|  \____|   /__/   \____|        All Rights Reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the 
 * GNU Affero General Public License as published by the Free Software Foundation, either version 
 * 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; 
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See 
 * the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this 
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.precog.accounts

import scala.collection.mutable

import com.precog.common.Path
import com.precog.common.accounts._
import com.precog.common.security._

import org.joda.time.DateTime
import org.bson.types.ObjectId

import scalaz._
import scalaz.syntax.monad._

class InMemoryAccountManager[M[+_]](implicit val M: Monad[M]) extends AccountManager[M] {
  val accounts = new mutable.HashMap[AccountId, Account]

  def updateAccount(account: Account): M[Boolean] = {
    findAccountById(account.accountId).map {
      case Some(acct) => accounts.put(account.accountId, account) ; true
      case _ => false
    }
  }

  def newAccount(email: String, password: String, creationDate: DateTime, plan: AccountPlan, parentId: Option[AccountId])(f: (AccountId, Path) => M[APIKey]): M[Account] = {
    TestAccounts.newAccount(email, password, creationDate, plan, parentId)(f) map { account =>
      accounts.put(account.accountId, account)
      account
    }
  }

  def findAccountByAPIKey(apiKey: APIKey) : M[Option[AccountId]] = {
    accounts.values.find(_.apiKey == apiKey).map(_.accountId).point[M]
  }

  def findAccountById(accountId: AccountId): M[Option[Account]] = accounts.get(accountId).point[M]

  def findAccountByEmail(email: String) : M[Option[Account]] = accounts.values.find(_.email == email).point[M]

  def deleteAccount(accountId: AccountId): M[Option[Account]] = accounts.remove(accountId).point[M]
}
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
package com.precog.yggdrasil
package actor

import com.precog.util._
import com.precog.common._
import com.precog.common.kafka._

import akka.actor._
import akka.dispatch._
import akka.util._
import akka.util.duration._
import akka.pattern.ask
import akka.pattern.gracefulStop

import _root_.kafka.consumer._

import blueeyes.json.JsonAST._

import com.weiglewilczek.slf4s.Logging

import scalaz._

import java.net.InetAddress

trait ProductionShardSystemConfig extends ShardConfig {
  def kafkaHost: String = config[String]("kafka.batch.host")
  def kafkaPort: Int = config[Int]("kafka.batch.port")
  def kafkaTopic: String = config[String]("kafka.batch.topic") 
  def kafkaSocketTimeout: Duration = config[Long]("kafka.socket_timeout", 5000) millis
  def kafkaBufferSize: Int = config[Int]("kafka.buffer_size", 64 * 1024)

  def zookeeperHosts: String = config[String]("zookeeper.hosts")
  def zookeeperBase: List[String] = config[List[String]]("zookeeper.basepath")
  def zookeeperPrefix: String = config[String]("zookeeper.prefix")   

  def serviceUID: ServiceUID = ZookeeperSystemCoordination.extractServiceUID(config)
  lazy val shardId = {
    val suid = serviceUID
    serviceUID.hostId + serviceUID.serviceId
  }
  val logPrefix = "[Production Yggdrasil Shard]"
}

trait ProductionShardSystemActorModule extends ShardSystemActorModule {
  type YggConfig <: ProductionShardSystemConfig

  def initIngestActor(checkpoint: YggCheckpoint, metadataActor: ActorRef) = {
    val consumer = new SimpleConsumer(yggConfig.kafkaHost, yggConfig.kafkaPort, yggConfig.kafkaSocketTimeout.toMillis.toInt, yggConfig.kafkaBufferSize)
    Some(() => new KafkaShardIngestActor(yggConfig.shardId, checkpoint, metadataActor, consumer, yggConfig.kafkaTopic, yggConfig.ingestEnabled))
  }

  def checkpointCoordination = ZookeeperSystemCoordination(yggConfig.zookeeperHosts, yggConfig.serviceUID, yggConfig.ingestEnabled) 
}

trait StandaloneShardSystemConfig extends ShardConfig {
  val shardId = "standalone"
  val logPrefix = "[Standalone Yggdrasil Shard]"
}

trait StandaloneShardSystemActorModule extends ShardSystemActorModule {
  type YggConfig <: StandaloneShardSystemConfig
  def initIngestActor(checkpoint: YggCheckpoint, metadataActor: ActorRef) = None
  def checkpointCoordination = CheckpointCoordination.Noop
}

// vim: set ts=4 sw=4 et:
/* tmux
type ShardSystemActorConfigs */

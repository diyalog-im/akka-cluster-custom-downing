package org.sisioh.akka.cluster.custom.downing

import akka.actor.Address
import akka.cluster.MemberStatus.Down
import akka.cluster.{ Member, MemberStatus }

import scala.concurrent.duration.FiniteDuration

abstract class QuorumLeaderAutoDownBase(
    quorumRole: Option[String],
    quorumSize: Int,
    downIfOutOfQuorum: Boolean,
    autoDownUnreachableAfter: FiniteDuration
) extends QuorumAwareCustomAutoDownBase(quorumSize, autoDownUnreachableAfter) {

  override def onLeaderChanged(leader: Option[Address]): Unit = {
    if (quorumRole.isEmpty && isLeader) downPendingUnreachableMembers()
  }

  override def onRoleLeaderChanged(role: String, leader: Option[Address]): Unit = {
    quorumRole.foreach { r =>
      if (r == role && isRoleLeaderOf(r)) downPendingUnreachableMembers()
    }
  }

  override def onMemberRemoved(member: Member, previousStatus: MemberStatus): Unit = {
    if (isQuorumMet(quorumRole)) {
      if (isLeaderOf(quorumRole)) {
        downPendingUnreachableMembers()
      }
    } else {
      down(selfAddress)
    }
    super.onMemberRemoved(member, previousStatus)
  }

  override protected def downOrAddPending(member: Member): Unit = {
    if (isLeaderOf(quorumRole)) {
      down(member.address)
      replaceMember(member.copy(Down))
    } else {
      pendingAsUnreachable(member)
    }
  }

  override protected def downOrAddPendingAll(members: Set[Member]): Unit = {
    if (isQuorumMetAfterDown(members, quorumRole)) {
      members.foreach(downOrAddPending)
    } else if (downIfOutOfQuorum) {
      shutdownSelf()
    }
  }
}
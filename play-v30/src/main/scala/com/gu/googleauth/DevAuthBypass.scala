package com.gu.googleauth

import play.api.{Environment, Mode}

/**
  * Opt-in bypass of Google authentication for local development and CI.
  *
  * Instances can only be created through [[DevAuthBypass.forMode]], which refuses to produce one in `Mode.Prod`.
  * While active, every request is treated as authenticated as `identity`, and Google group checks are skipped.
  */
final class DevAuthBypass private (val identity: UserIdentity)

object DevAuthBypass {
  /** An obviously fake identity; `.invalid` is a reserved TLD so it can never match a real account. */
  val defaultIdentity: UserIdentity =
    UserIdentity("dev-bypass", "dev-user@example.invalid", "Dev", "User", Long.MaxValue / 1000, None)

  /**
    * @param mode the Play mode the application is running in
    * @param enabled an explicit opt-in flag, which must be false by default and never set in production config
    * @return a bypass only if `enabled` and the mode is `Dev` or `Test`
    * @throws java.lang.IllegalStateException if `enabled` is true in `Prod` so a misconfiguration fails at startup
    */
  def forMode(mode: Mode, enabled: Boolean = false, identity: UserIdentity = defaultIdentity): Option[DevAuthBypass] =
    (mode, enabled) match {
      case (Mode.Prod, true) =>
        throw new IllegalStateException("play-googleauth dev bypass must not be enabled in Prod mode")
      case (Mode.Dev | Mode.Test, true) => Some(new DevAuthBypass(identity))
      case _ => None
    }

  def forEnvironment(environment: Environment, enabled: Boolean, identity: UserIdentity = defaultIdentity): Option[DevAuthBypass] =
    forMode(environment.mode, enabled, identity)
}

package com.gu.googleauth

import play.api.{Environment, Logging, Mode}

/**
  * Opt-in bypass of Google authentication for local development and CI.
  *
  * Instances can only be created through [[DevAuthBypass.forEnvironment]], which refuses to produce one in `Mode.Prod`.
  * The identity must use a reserved `.invalid` email domain.
  * While active, every request is treated as authenticated as `identity`, and Google group checks are skipped.
  */
final class DevAuthBypass private (val identity: UserIdentity)

object DevAuthBypass extends Logging {
  /** An obviously fake identity; `.invalid` is a reserved TLD so it can never match a real account. */
  val defaultIdentity: UserIdentity =
    UserIdentity("dev-bypass", "dev-user@example.invalid", "Dev", "User", Long.MaxValue / 1000, None)

  /**
    * @param environment the running application's Play environment
    * @param enabled an explicit opt-in flag, which must be false by default and never set in production config
    * @param identity a fake identity with an email address under the reserved `.invalid` domain
    * @return a bypass only if `enabled` and the mode is `Dev` or `Test`
    * @throws java.lang.IllegalStateException if `enabled` is true in `Prod` so a misconfiguration fails at startup
    * @throws java.lang.IllegalArgumentException if `enabled` and the identity does not have a `.invalid` email domain
    */
  def forEnvironment(environment: Environment, enabled: Boolean, identity: UserIdentity = defaultIdentity): Option[DevAuthBypass] =
    forMode(environment.mode, enabled, identity)

  private def forMode(mode: Mode, enabled: Boolean, identity: UserIdentity): Option[DevAuthBypass] =
    (mode, enabled) match {
      case (Mode.Prod, true) =>
        throw new IllegalStateException("play-googleauth dev bypass must not be enabled in Prod mode")
      case (Mode.Dev | Mode.Test, true) =>
        require(
          identity.email.endsWith(".invalid"),
          "play-googleauth dev bypass identity must have an email address under the reserved .invalid domain"
        )
        logger.warn(
          "WARNING: PLAY-GOOGLEAUTH DEV BYPASS IS ACTIVE. Every request is treated as authenticated " +
            "and Google Group checks are skipped. NEVER enable this on a production application."
        )
        Some(new DevAuthBypass(identity))
      case _ => None
    }
}

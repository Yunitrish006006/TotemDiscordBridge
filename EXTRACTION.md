# Extraction contract

This module owns Discord transport/configuration, event formatting and dispatch,
Discord payloads and receivers, Discord Mixins, language resources, client
configuration UI, and its server/client bootstrap. Its public identifiers and
configuration path remain compatible with the DeadRecall bundle during the
observation window.

The first copy is additive: DeadRecall's old implementation remains behind one
bootstrap choice until standalone and bundle validation prove exactly one
registration path is active.

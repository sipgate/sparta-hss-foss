/// The framework-agnostic HSS core.
///
/// Transaction contract: handlers and services demarcate their transaction boundaries with
/// [jakarta.transaction.Transactional] (`REQUIRED` semantics, rollback on unchecked exceptions).
/// The hosting application must run them in a container that honors that annotation — Spring
/// (see `sparta-hss-spring-boot`) and CDI containers do. Instances constructed directly with
/// `new` are NOT transactional; an embedder without a container must wrap the calls in
/// transactions itself.
package com.sipgate.sparta.hss;

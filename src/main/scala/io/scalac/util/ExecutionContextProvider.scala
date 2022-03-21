package io.scalac.util

import scala.concurrent.ExecutionContext

trait ExecutionContextProvider {
  val cpuBoundExCtx: ExecutionContext
  val ioBoundExCtx: ExecutionContext
  val eventLoopExCtx: ExecutionContext
}

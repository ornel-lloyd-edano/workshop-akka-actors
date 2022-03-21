package io.scalac.util

import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext

object ExecutionContexts extends ExecutionContextProvider {
  private def getFixedThreadPoolExecutionContext(threads: Int):ExecutionContext = new ExecutionContext {
    private val threadPool = Executors.newFixedThreadPool(threads)
    override def execute(runnable: Runnable): Unit = threadPool.submit(runnable)
    override def reportFailure(cause: Throwable): Unit = cause.printStackTrace()
  }
  val cpuBoundExCtx = getFixedThreadPoolExecutionContext(Configs.getIntConfigVal("execution-contexts.cpu-bound.num-threads").getOrElse(2))
  val ioBoundExCtx = getFixedThreadPoolExecutionContext(Configs.getIntConfigVal("execution-contexts.io-bound.num-threads").getOrElse(4))
  val eventLoopExCtx = getFixedThreadPoolExecutionContext(Configs.getIntConfigVal("execution-contexts.event-loop.num-threads").getOrElse(1))
}
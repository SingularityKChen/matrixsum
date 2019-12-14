package matrixsum.tests

import chisel3._
import matrixsum._
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}
import freechips.rocketchip.config.Parameters
class AccumModuleSpecIotesters(implicit val p: Parameters)(c: AccumModule) extends PeekPokeTester (c) {
//class AccumModuleSpecIotesters(c: AccumModule) extends PeekPokeTester (c) {
  poke(c.io.init, true.B)
  step(5)
  poke(c.io.req_data, 20)
  step(1)
  expect(c.io.resp_data, 20, s"req_data = 20 + 12, ${peek(c.io.resp_data)}, overflow = ${peek(c.io.overflow)}")
  step(1)
  c.io.req_data.poke(12)
  step(1)
  expect(c.io.resp_data, 12, s"req_data = 20 + 12, ${peek(c.io.resp_data)}, overflow = ${peek(c.io.overflow)}")
  step(1)
  c.io.req_data.poke(5)
  step(1)
  c.io.resp_data.expect(37, s"req_data = 20 + 12 + 5, ${peek(c.io.resp_data)}, overflow = ${peek(c.io.overflow)}")
  step(1)
  c.io.resp_data.expect(37, s"req_data = 20 + 12 + 5, ${peek(c.io.resp_data)}, overflow = ${peek(c.io.overflow)}")
}

assert(chisel3.iotesters.Driver(() => new AccumModule) {c => new AccumModuleSpecIotesters(c) })
println("SUCCESS!!")

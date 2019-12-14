package matrixsum.tests

import chisel3._
import chiseltest._
import matrixsum._
import freechips.rocketchip.config.Parameters
import org.scalatest._

class AccumModuleSpec(implicit val p: Parameters) extends FlatSpec with ChiselScalatestTester {
  it should "base function" in {
    test(new AccumModule(10)(p)) { c =>
      c.io.init.poke(true.B)
      c.clock.step(5)
      c.io.req_data.poke(20.U)
      c.clock.step(1)
      c.io.resp_data.expect(20.U, "req_data = 20")
      c.clock.step(1)
      c.io.req_data.poke(12.U)
      c.clock.step(1)
      c.io.resp_data.expect(12.U, "req_data = 20 + 12")
      c.clock.step(1)
      c.io.req_data.poke(5.U)
      c.clock.step(1)
      c.io.resp_data.expect(37.U, "req_data = 20 + 12 + 5")
      c.clock.step(1)
      c.io.resp_data.expect(37.U, "req_data = 20 + 12 + 5")
    }
  }
}

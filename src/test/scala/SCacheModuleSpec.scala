package matrixsum.test

import chisel3._
import chiseltest._
import matrixsum._
import freechips.rocketchip.config.Parameters
import org.scalatest._

//class ScacheModuleSpec extends FreeSpec with ChiselScalatestTester {
//class ScacheModuleSpec extends FreeSpec with ChiselScalatestTester {
class SCacheModuleSpec(implicit val p: Parameters) extends FlatSpec with ChiselScalatestTester {
  it should "run" in {
    test(new SCacheModule(64)(p)) { c =>
      c.io.init.poke(true.B)
      c.clock.step(5)
      c.io.row_or_col.poke(false.B)//column sum
      c.io.req_data.poke(20.U)
      c.io.resp_rdy.poke(true.B)
      c.io.index.poke(0.U)
      c.clock.step(2)
      c.io.resp_rdy.poke(false.B)//1
      c.io.req_data.poke(12.U)
      c.io.resp_rdy.poke(true.B)
      c.io.index.poke(3.U)
      c.clock.step(2)
      c.io.resp_rdy.poke(false.B)//2
      c.io.req_data.poke(5.U)
      c.io.resp_rdy.poke(true.B)
      c.io.index.poke(2.U)
      c.clock.step(2)
      c.io.resp_rdy.poke(false.B)//3
      c.io.req_val.poke(true.B) //read
      c.io.index.poke(2.U)
      val out_clk_0 = c.io.resp_data.peek()
      c.io.resp_data.expect(5.U, "clock 0")
      c.clock.step(1)
      val out_clk_1 = c.io.resp_data.peek()
      c.io.resp_data.expect(5.U, "clock 1")
      c.io.resp_data.peek()
      c.clock.step(1)
      val out_clk_2 = c.io.resp_data.peek()
      c.io.resp_data.expect(5.U, "clock 2")
      c.io.resp_data.peek()
      c.clock.step(1)
      val out_clk_3 = c.io.resp_data.peek()
      c.io.resp_data.expect(5.U, "clock 3")
    }
  }
}

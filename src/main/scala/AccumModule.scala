package matrixsum

import chisel3._
import freechips.rocketchip.config.{Field, Parameters}

import scala.math._
import chisel3.util._
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

class AccumModule(val W: Int)(implicit val p: Parameters) extends Module {
  val overflow_dmem_data : Int = pow(2,W).toInt //scala type, need import scala.math._
  val io = IO(new Bundle{
    val req_data = Input(UInt(W.W))
    val resp_data = Output(UInt(W.W))
    val init = Input(Bool())
    val overflow = Output(Bool())
    
  })
  val accum_sum = RegInit(0.U((W+1).W)) //a bit more to indicate overflow
  val overflow_reg = RegInit(false.B)
  overflow_reg := accum_sum(W) //if the highest bit in accum_sum is one, means overflow
  accum_sum := accum_sum + io.req_data
  io.resp_data := Mux(overflow_reg, overflow_dmem_data.U(W.W), accum_sum)
  io.overflow := overflow_reg

  
  when(io.init){
    accum_sum := 0.U
    overflow_reg := false.B
    }
}

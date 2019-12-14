package matrixsum

import chisel3._
import freechips.rocketchip.config.{Field, Parameters}
//import freechips.rocketchip.tile.HasCoreParameters
//import freechips.rocketchip.rocket.{HellaCacheReq, MStatus, PRV, TLB, TLBConfig, TLBPTWIO}
//import freechips.rocketchip.diplomacy._
//import freechips.rocketchip.tilelink._

class SCacheModule(val W: Int)(implicit p: Parameters) extends Module {

  val io = IO(new Bundle {
    val init = Input(Bool())
    val index = Input(UInt(5.W)) //the index which indicates the column or row particular sum

    val req_data = Input(UInt(W.W))
    val req_val = Input(Bool())
    val resp_data = Output(UInt(W.W))
    val resp_rdy = Input(Bool())
    val row_or_col = Input(Bool()) //column sum (0,false) or row sum (1,true)

  })
  //maximum matrix size equals to 31, so we need 31 depth SRAM
  val row_particular_sum = SyncReadMem(UInt(W.W), 31) //to store W-bit particular sums produced during row sum caculation. 
  val col_particular_sum = SyncReadMem(UInt(W.W), 31) //to store W-bit particular sums produced during column sum caculation. 
  when(io.row_or_col){ //if the accelerator is calculating row sum
    when(io.req_val){ //if we need the particular sum in SCacheModule
      io.resp_data := col_particular_sum.read(io.index) //then we need to read it from column particular sum
    }
    when(io.resp_rdy){ 
      row_particular_sum.write(io.index, io.resp_data + row_particular_sum.read(io.index))
    }
  }.otherwise{ //if the accelerator is caculating column sum
    when(io.req_val){ //if we need the particular sum in SCacheModule
      io.resp_data := row_particular_sum.read(io.index)//then we need to read it from row particular sum
    }
    when(io.resp_rdy){ 
      col_particular_sum.write(io.index, io.resp_data + col_particular_sum.read(io.index)) //we need to write it back after plus the original one and the new coming one
    }
  }

  when(io.init) {
    for (i <- 0 until W - 1){
      row_particular_sum.write(i.U, 0.U)
      col_particular_sum.write(i.U, 0.U)
    }
    
  }
}





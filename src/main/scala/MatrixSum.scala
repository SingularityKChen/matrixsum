package matrixsum

import Chisel._
import freechips.rocketchip.tile._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.rocket.{TLBConfig, HellaCacheReq}

case object MatrixSumWidthP extends Field[Int]
//case object MatrixSumFastMem extends Field[Boolean]
//case object MatrixSumBufferSram extends Field[Boolean]
/*
 * Enable specific printf's
 */
//case object MatrixSumPrintfEnable extends Field[Boolean](false)

class MatrixSum(opcodes: OpcodeSet)(implicit p: Parameters) extends LazyRoCC(
    opcodes = opcodes, nPTWPorts = if (p(MatrixSumTLB).isDefined) 1 else 0) {
  override lazy val module = new MatrixSumImp(this)
  val dmemOpt = p(MatrixSumTLB).map { _ =>
    val dmem = LazyModule(new DmemModule) 
    tlNode := dmem.node
    dmem
  }
}

class MatrixSumImp(outer: MatrixSum)(implicit p: Parameters) extends LazyRoCCModuleImp(outer) {
  val W = p(MatrixSumWidthP)  //the width of each number
  //io.cmd <> core
  //memory <> io.mem <> dmem <> tlb
  //
  val ctrl = Module(new CtrlModule(W)(p))
  ctrl.io.rocc_req_val   <> io.cmd.valid
  io.cmd.ready := ctrl.io.rocc_req_rdy
  ctrl.io.rocc_funct     <> io.cmd.bits.inst.funct
  ctrl.io.rocc_rs1       <> io.cmd.bits.rs1
  ctrl.io.rocc_rs2       <> io.cmd.bits.rs2
  ctrl.io.rocc_rd        <> io.cmd.bits.inst.rd
  io.busy := ctrl.io.busy

  val dmem_data = Wire(Bits())
  private def dmem_ctrl(req: DecoupledIO[HellaCacheReq]) { //required by dmem, connections between DmemModule and CtrlModule
    req.valid := ctrl.io.dmem_req_val
    ctrl.io.dmem_req_rdy := req.ready
    req.bits.tag := ctrl.io.dmem_req_tag
    req.bits.addr := ctrl.io.dmem_req_addr
    req.bits.cmd := ctrl.io.dmem_req_cmd
    req.bits.size := ctrl.io.dmem_req_size
    req.bits.data := dmem_data //the result which is required by memory
    //req.bits.signed := Bool(false)
    //req.bits.phys := Bool(false)
  }

  outer.dmemOpt match {
    case Some(m) => {
      val dmem = m.module
      dmem_ctrl(dmem.io.req)
      io.mem.req <> dmem.io.mem // the dmem module control the require part
      io.ptw.head <> dmem.io.ptw

      dmem.io.status.valid := io.cmd.fire()
      dmem.io.status.bits := io.cmd.bits.status
      dmem.io.sfence := ctrl.io.sfence
    }
    case None => dmem_ctrl(io.mem.req)
  }

  ctrl.io.dmem_resp_val  <> io.mem.resp.valid
  ctrl.io.dmem_resp_tag  <> io.mem.resp.bits.tag
  ctrl.io.dmem_resp_data := io.mem.resp.bits.data //data from top to CtrlModule


  // the connections between top and AccumModule
  val accum = Module(new AccumModule(W)(p))
  val scache = Module(new SCacheModule(W)(p))

  dmem_data := ctrl.io.dmem_req_data //inorder to response 64'b1 while overflowing, we need the CtrlModule to response the caculated result.

  //ctrl.io <> scache.io
  scache.io.req_val := ctrl.io.scache_req_val
  scache.io.req_data := ctrl.io.scache_req_data //   io.message_in     the input data which is required by MatrixSum, translate between AccumModule and CtrlModule
  scache.io.resp_rdy := ctrl.io.scache_resp_rdy
  ctrl.io.scache_resp_data := scache.io.resp_data
  scache.io.index := ctrl.io.scache_index
  scache.io.init := ctrl.io.init
  scache.io.row_or_col := ctrl.io.scache_req_row_or_col

  //ctrl.io <> accum.io

  accum.io.req_data := ctrl.io.accum_req_data //the input data which is required by MatrixSum, translate between AccumModule and CtrlModule
  ctrl.io.accum_resp_data := accum.io.resp_data
  accum.io.overflow := ctrl.io.accum_overflow
  accum.io.init := ctrl.io.init

}

class WithMatrixSum extends Config ((site, here, up) => {
  case MatrixSumWidthP => 64
  case MatrixSumTLB => Some(TLBConfig(nEntries = 4, nSectors = 1, nSuperpageEntries = 1))
  case BuildRoCC => Seq(
    (p: Parameters) => {
      val matrixsum = LazyModule.apply(new MatrixSum(OpcodeSet.custom0)(p))
      matrixsum
    }
  )
})

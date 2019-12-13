package matrixsum

import Chisel._
import scala.math._
import Chisel.ImplicitConversions._
import freechips.rocketchip.tile.HasCoreParameters
import freechips.rocketchip.rocket.constants.MemoryOpConstants
import freechips.rocketchip.config._
//import chisel3.util.PriorityEncoder

//import chisel3._

class CtrlModule(val W: Int)(implicit val p: Parameters) extends Module
  with HasCoreParameters
  with MemoryOpConstants {
  val r = 2*256 //512
  val c = 25*W - r //
  val round_size_words = c/W // = 25-512/W
  val overflow_dmem_data = pow(2,W) //scala type, need import scala.math._
  val maximum_matrix_size = 31 //scala type

  val io = new Bundle{

    //related to RoCC
    val rocc_req_val      = Bool(INPUT)
    val rocc_req_rdy      = Bool(OUTPUT) //if true, then ask core for instructions
    val rocc_funct        = Bits(INPUT, 2)
    val rocc_rs1          = Bits(INPUT, 5)
    val rocc_rs2          = Bits(INPUT, 5)
    val rocc_rd           = Bits(INPUT, 5)

    val busy              = Bool(OUTPUT)

    //*************************************//
    //********* DmemModule Begin **********//
    val dmem_req_val      = Bool(OUTPUT)
    val dmem_req_rdy      = Bool(INPUT)
    val dmem_req_tag      = Bits(OUTPUT, dcacheReqTagBits)
    val dmem_req_addr     = Bits(OUTPUT, coreMaxAddrBits) //send dmem the data address to store the data
    val dmem_req_data     = Bits(OUTPUT, W) //send the data back to dmem
    val dmem_req_cmd      = Bits(OUTPUT, M_SZ)
    val dmem_req_size     = Bits(OUTPUT, log2Ceil(coreDataBytes + 1))
    val dmem_resp_val     = Bool(INPUT)
    val dmem_resp_tag     = Bits(INPUT, 7)
    val dmem_resp_data    = Bits(INPUT, W) //receive the element from dmem
    val sfence            = Bool(OUTPUT)
    //********** DmemModule End ***********//
    //*************************************//

    //*************************************//
    //******** SCacheModule Begin *********//
    //if SCacheModule required, it's the element
    val scache_req_val    = Bool(OUTPUT) //CrtlModule is valid
    val scache_req_data   = Bits(OUTPUT, W) //send one element to SCacheModule, store it and caculate the particular sum
    val scache_req_row_or_col = Bool(OUTPUT) //tell SCacheModule we are doing column sum (0,false) or row sum (1,true)
    val scache_index  = Bits(OUTPUT, 6) //send the index of the element or 
    // if SCacheModule response, it's the particular sum
    val scache_resp_rdy   = Bool(OUTPUT)
    val scache_resp_data  = Bits(INPUT, W) //receive the particular sum from SCacheModuleparticular sum SCacheModule
    //********* SCacheModule End **********//
    //*************************************//

    //*************************************//
    //********* AccumModule Begin *********//
    val accum_req_data    = Bits(OUTPUT, W) //the element to be caculated
    val accum_resp_data   = Bits(INPUT, W) //the column sum or cow sum
    val accum_overflow    = Bool(INPUT) //the accumulator is overflow
    //val accum_matrix_size = Bits(OUTPUT, 6)
    //********** AccumModule End **********//
    //*************************************//

    //related to both modules
    val init              = Bool(OUTPUT) //initial

  }
  
  //accum_buffer_out euqls to mux(ctrl.sel, element_in, scache.io.resp.data)
  //including encode the instruction
  io.init := Bool(false)
  val busy_reg = Reg(init=Bool(false))
  io.busy := busy_reg
  val rocc_req_rdy_reg = Reg(init=Bool(false))
  io.rocc_req_rdy := rocc_req_rdy_reg
  //rocc pipe state, the communication state between rocc and CrtlModule. Only related to instructions
  //rocc_idle: the RoCC Accelerator is not busy, so can receive new instruction
  //rocc_processing: the CrtlModule is waiting for the process of its submodules, including data access
  //rocc_write_back: write back the results to destination direction, so next time it can ask for a new instruction
  val rocc_idle :: rocc_processing :: rocc_write_back :: Nil = Enum(UInt(), 3)
  val rocc_state = Reg(init=rocc_idle) 
  //memory pipe state, the communication state between memory and CrtlModule. Only related to data
  //mem_idle: memory is not busy, so Accelerator can send it read or write command
  //mem_read: Accelerator is asking memory to read some data and unfinished to send the read signals.
  //mem_write: Accelerator is asking memory to write back data and unfinished. Don't need to jump to wait because it just has one data to write back.
  val mem_idle :: mem_read ::  mem_write :: Nil = Enum(UInt(), 3)
  val mem_state = Reg(init=mem_idle)
  //ctrl pipe state, the communication state between CrtlModule and its two submodules
  //ctrl_idle: the CrtlModule is not busy, so can send elements to submodules
  //ctrl_send: continue sending the data to submodules, wait for one resp valid signal, then jump to wait another
  //ctrl_wait_resp: ask SCacheModule for particular sum
  val ctrl_idle :: ctrl_send :: ctrl_wait_resp :: Nil = Enum(UInt(), 3)
  val ctrl_state = Reg(init=ctrl_idle)
  //register inputs 
  val rocc_req_val_reg = Reg(next=io.rocc_req_val)
  val rocc_funct_reg = Reg(init = Bits(0,2))
  rocc_funct_reg := io.rocc_funct
  val rocc_rs1_reg = Reg(next=io.rocc_rs1)
  val rocc_rs2_reg = Reg(next=io.rocc_rs2)
  val rocc_rd_reg = Reg(next=io.rocc_rd)
  val dmem_req_rdy_reg = Reg(next=io.dmem_req_rdy)
  val dmem_resp_val_reg = Reg(next=io.dmem_resp_val)
  val dmem_resp_tag_reg = Reg(next=io.dmem_resp_tag)
  val dmem_resp_data_reg = Reg(next=io.dmem_resp_data)
  val accum_resp_data_reg = Reg(next=io.accum_resp_data)
  val accum_overflow_reg = Reg(init = Bool(false))
  accum_overflow_reg := io.accum_overflow
  val scache_resp_data_reg = Reg(next=io.scache_resp_data)
  val scache_req_val_reg = Reg(init = Bool(false)) //if true, then ask SCacheModule for data
  io.scache_req_val := scache_req_val_reg
  //initial output signals
  io.dmem_req_val:= Bool(false) // initial the valid signal to not ready
  io.dmem_req_addr:= Bits(0, 32) //initial the address to zero
  io.dmem_req_cmd:= M_XRD // read only
  io.dmem_req_size:= log2Ceil(8).U //the sieze of the data is 2^8 = 64-bit
  io.sfence      := Bool(false)
  val ask_memory_read = Reg(init = Bool(false)) //if true, then can ask memory for data
  val ask_memory_write = Reg(init = Bool(false)) //if true, then can tell memory we are going to write data
  val matrix_start_addr = Reg(init = UInt(0, 6)) // to store the start address of matrix
  val matrix_index = Reg(init = UInt(0, log2Up(W))) // to store the current column sum's or row sum's index
  val matrix_size = Reg(init = UInt(0,5)) // to store the size of the matrix
  val req_addr_reg = Reg(init = UInt(0, 5)) // to store the address that is needed to write back
  val next_element_addr = Reg(init = UInt(0, 6)) //the address of the data need to be read
  val current_element_data = Reg(init = UInt(0, 64)) //the data read from memory
  val col_no_index = Reg( init = Vec.fill(maximum_matrix_size){Bool(true)}) //store the index of the columns that have NOT done sum, TRUE means not done, FALSE means have done
  val row_no_index = Reg( init = Vec.fill(maximum_matrix_size){Bool(true)}) //store the index of the rows that have NOT done sum, TRUE means not done, FALSE means have done
  val not_send_memory_matrix_positions = Reg( init = Vec.fill(maximum_matrix_size){Bool(true)}) // store the positions that have NOT ask memory for data in one single colsum or rowsum instruction, TRUE means not done, FALSE means have done
  val not_receive_matrix_positions = Reg( init = Vec.fill(maximum_matrix_size){Bool(true)}) // store the positions that have NOT received the data in one single colsum or rowsum instruction, TRUE means not done, FALSE means have done
  val send_memory_matrix_position = Reg(init = UInt(0,log2Up(maximum_matrix_size)))//store the position that is sent to ask for data
  val not_send_submodules_matrix_positions = Reg( init = Vec.fill(maximum_matrix_size){Bool(true)})//store the positions that have NOT sent to its submodules, TRUE means not done, FALSE means have done

  //rocc states,
  switch(rocc_state) {
    is(rocc_idle) {
      when(rocc_req_val_reg && !busy_reg){ //when the core can send instructions and the Accelerator is not busy
        io.accum_overflow := Bool(false)
        when(rocc_funct_reg === UInt(0)){ //setup
          busy_reg := Bool(false)// then ask for a new instruction
          rocc_req_rdy_reg := Bool(true) // then ask for a new instruction
          matrix_start_addr  := io.rocc_rs1 //the statring memory address of the matrix
          matrix_size := io.rocc_rs2 //the size of the matrix, up to 31
          io.init := Bool(true) //ask the two submodules to prepare for the new turn
          println("Matrix Starte Address: "+matrix_start_addr+", Matrix Size: "+matrix_size)
          rocc_state := rocc_idle // the next state, receive another instruction
          /*for (w <- 0 until matrix_size){
            col_no_index(w) := Bool(true)
          }
          */
          send_memory_matrix_position := UInt(0) //Default to first position in one column or row
          for (i <- 0 until maximum_matrix_size) {
            when(matrix_size === UInt(i)){
              for (j <- 0 until i -1 ){
                col_no_index(j) := Bool(true)//Default never calculate any column value
                col_no_index(j) := Bool(true)//Default never calculate any column value
                row_no_index(j) := Bool(true)//Default never calculate any row value
                not_send_memory_matrix_positions(j) := Bool(true)//Default never send any
                not_receive_matrix_positions(j) := Bool(true)//Default never receive any
                not_send_submodules_matrix_positions(j) := Bool(true)//Default never send any
              }
              //the other need to set false to close them
              if (i < maximum_matrix_size) {
                for (k <- i until maximum_matrix_size -1 ) {
                  col_no_index(k) := Bool(false)//Default never calculate any column value
                  col_no_index(k) := Bool(false)//Default never calculate any column value
                  row_no_index(k) := Bool(false)//Default never calculate any row value
                  not_send_memory_matrix_positions(k) := Bool(false)//Default never send any
                  not_receive_matrix_positions(k) := Bool(false)//Default never receive any
                  not_send_submodules_matrix_positions(k) := Bool(false)//Default never send any
                }
              }
            }
          }
          /*
          col_no_index(matrix_size - 1,0) := Vec.fill(matrix_size){Bool(true)}//Default never calculate any column value
          row_no_index(matrix_size - 1,0) := Vec.fill(matrix_size){Bool(true)}//Default never calculate any row value
          not_send_memory_matrix_positions(matrix_size - 1,0) := Vec.fill(matrix_size){Bool(true)}//Default never send any
          not_receive_matrix_positions(matrix_size - 1,0) := Vec.fill(matrix_size){Bool(true)}//Default never receive any
          not_send_submodules_matrix_positions(matrix_size - 1,0) := Vec.fill(matrix_size){Bool(true)}//Default never send any
          */

          //the other need to set false to close them
          /*
          when(matrix_size < UInt(maximum_matrix_size -1)){ //set the unused reg to false
            col_no_index(maximum_matrix_size - 1, matrix_size) := Vec.fill(maximum_matrix_size - matrix_size){Bool(false)}
            row_no_index(maximum_matrix_size - 1, matrix_size) := Vec.fill(maximum_matrix_size - matrix_size){Bool(false)}
            not_send_memory_matrix_positions(maximum_matrix_size - 1, matrix_size) := Vec.fill(maximum_matrix_size - matrix_size){Bool(false)}
            not_receive_matrix_positions(maximum_matrix_size - 1, matrix_size) := Vec.fill(maximum_matrix_size - matrix_size){Bool(false)}
            not_send_submodules_matrix_positions(maximum_matrix_size - 1, matrix_size) := Vec.fill(maximum_matrix_size - matrix_size){Bool(false)}
          }.elsewhen(matrix_size === UInt(maximum_matrix_size - 1)){
            col_no_index(maximum_matrix_size - 1) := Bool(false)
            row_no_index(maximum_matrix_size - 1) := Bool(false)
            not_send_memory_matrix_positions(maximum_matrix_size - 1) := Bool(false)
            not_receive_matrix_positions(maximum_matrix_size - 1) := Bool(false)
            not_send_submodules_matrix_positions(maximum_matrix_size - 1) := Bool(false)
          }
          */

        }.elsewhen(rocc_funct_reg === UInt(1)) { //rowsum
          busy_reg := Bool(true) 
          rocc_req_rdy_reg := Bool(false) //next time, the Accelerator will process this instruction rather than ask for a new one
          req_addr_reg := io.rocc_rd //the required results's address
          matrix_index := io.rocc_rs1 //the 0-indexed row to be summed
          row_no_index(matrix_index) := Bool(false) //this row has asked for element
          not_send_submodules_matrix_positions := col_no_index // if any columns have done sum, means there are some row particular sums in SCacheModule, and don't need to ask for these elements
          send_memory_matrix_position := PriorityEncoder(col_no_index & not_send_memory_matrix_positions) //find the first index whose value is "1", which means hasn't caculated before in this variable. So we need to read its data
          next_element_addr := matrix_start_addr + (matrix_size*matrix_index + PriorityEncoder(col_no_index & not_send_memory_matrix_positions))*W//the address the first index which is one(in this variable, that means hasn't asked for element data)
          not_receive_matrix_positions := col_no_index & not_receive_matrix_positions //if we have done some column sums, then we can save some read requirements
          not_send_memory_matrix_positions := col_no_index & not_send_memory_matrix_positions //if we have done some column sums, then we can save some read requirements

          when(!col_no_index(matrix_size - 1,0).reduce(_ && _)){ //if the Accelerator has done some column sum
            ask_memory_read := Bool(false) //next time, ask SCacheModule for data rather than memory
            scache_req_val_reg := Bool(true) //then ask for the corresponding particular sum
            io.scache_req_row_or_col := Bool(true) //tell SCacheModule we are doing row sum
          }.otherwise{
            ask_memory_read := Bool(true) //then ask memory for data
            scache_req_val_reg := Bool(false)
          }
          println("The current row sum index: "+matrix_index+", the results will be write back to"+req_addr_reg+", now read the first elements. The index of the next element to read is "+send_memory_matrix_position+"and the corresponding address is "+next_element_addr)
          rocc_state := rocc_processing // the next state
        }.elsewhen(rocc_funct_reg === UInt(2)) { //colsum
          busy_reg := Bool(true) 
          rocc_req_rdy_reg := Bool(false)//next time, the Accelerator will process this instruction rather than ask for a new one
          req_addr_reg := io.rocc_rd //the required results's address
          matrix_index := io.rocc_rs1 //the 0-indexed column to be summed
          col_no_index(matrix_index) := Bool(false) //this column has asked for data
          not_send_submodules_matrix_positions := row_no_index // if any row have done sum, means there are some column particular sums in SCacheModule, and don't need to ask for these elements
          send_memory_matrix_position := PriorityEncoder(row_no_index & not_send_memory_matrix_positions) //find the first index whose value is "1", which means hasn't caculated before in this variable. So we need to read its data FIXME: rewrite the module
          next_element_addr := matrix_start_addr + (matrix_index + PriorityEncoder(row_no_index & not_send_memory_matrix_positions)*matrix_size)*W // the address of the first index which is one(in this variable, that means hasn't asked for element data)
          not_receive_matrix_positions := row_no_index & not_receive_matrix_positions //if we have done some column sums, then we can save some read requirements
          not_send_memory_matrix_positions := row_no_index & not_send_memory_matrix_positions //if we have done some column sums, then we can save some read requirements

          when(!row_no_index(matrix_size - 1,0).reduce(_&&_)){//if the Accelerator has done some row sum
            ask_memory_read := Bool(false) //next time, ask SCacheModule for data rather than memory
            scache_req_val_reg := Bool(true) //then ask for the corresponding particular sum
            io.scache_req_row_or_col := Bool(false)//tell SCacheModule we are doing column sum
          }.otherwise{
            ask_memory_read := Bool(true) //then ask memory for data
            scache_req_val_reg := Bool(false)
          }
          println("The current column sum index: "+matrix_index+", the results will be write back to"+req_addr_reg+", now read the first elements. The index of the next element to read is "+send_memory_matrix_position+"and the corresponding address is "+next_element_addr)
          rocc_state := rocc_processing // the next state
        }
      }
    }
    is(rocc_processing) {
      busy_reg := Bool(true)
      rocc_req_rdy_reg := Bool(false)
      when(mem_state === mem_write) {
        rocc_state := rocc_write_back
      }.otherwise{
        rocc_state := rocc_processing
      }

    }
    is(rocc_write_back) {
      when(mem_state === mem_write && dmem_req_rdy_reg){
        busy_reg := Bool(false)// then ask for a new instruction
        rocc_req_rdy_reg := Bool(true)// then ask for a new instruction
        rocc_state := rocc_idle
      }.otherwise{
        rocc_state := rocc_write_back
      }
    }

  }

  //memory states
  switch(mem_state) {
    is(mem_idle) {
      io.dmem_req_val := ask_memory_read && not_send_memory_matrix_positions(matrix_size - 1,0).reduce(_||_)//ask for data when we need to read and there is any elements haven't send read FIXME
      io.dmem_req_cmd := M_XRD // means read
      io.dmem_req_tag := send_memory_matrix_position //equals to UInt(0), the first position in current row
      io.dmem_req_data := Bits(0, 64) //we need to read data from memory
      io.dmem_req_addr := next_element_addr 
      when(dmem_req_rdy_reg && ask_memory_read && not_send_memory_matrix_positions(matrix_size - 1,0).reduce(_||_)){ //FIXME
        when(rocc_funct_reg === UInt(1)){
            not_send_memory_matrix_positions(send_memory_matrix_position) := Bool(false) //this position has asked for data
            send_memory_matrix_position := PriorityEncoder(not_send_memory_matrix_positions) //return the first index which is one(in this variable, that means hasn't asked for element data)
            next_element_addr := matrix_start_addr + (matrix_size*matrix_index + PriorityEncoder(not_send_memory_matrix_positions))*W//the address the first index which is one(in this variable, that means hasn't asked for element data)
        }
        when(rocc_funct_reg === UInt(2)){
            not_send_memory_matrix_positions(send_memory_matrix_position) := Bool(false) //this position has asked for data
            send_memory_matrix_position := PriorityEncoder(not_send_memory_matrix_positions)//return the first index which is one(in this variable, that means hasn't asked for element data)
            next_element_addr := matrix_start_addr + (matrix_index + PriorityEncoder(not_send_memory_matrix_positions)*matrix_size)*W // the address of the first index which is one(in this variable, that means hasn't asked for element data)
        }
        mem_state := mem_read
      }.otherwise{ // wait for memory being ready or read signal
        mem_state := mem_idle
      }
      when(!not_send_memory_matrix_positions(matrix_size - 1,0).reduce(_||_)){ //if we don't need to ask memory for any data
        mem_state := mem_write //then wait for write back
      }

    }
    is(mem_read) {
      when(!not_send_memory_matrix_positions(matrix_size - 1,0).reduce(_||_)) { // all the elements have asked for data FIXME: use for loop to do that
        mem_state := mem_write // jump to mem_write to wait for write back
        ask_memory_read := Bool(false) //read finish
      }.otherwise {
        mem_state := mem_read // send more requirements for data
        io.dmem_req_val := ask_memory_read//ask for data when we need to read
        io.dmem_req_cmd := M_XRD //means read
        io.dmem_req_tag := send_memory_matrix_position
        io.dmem_req_data := Bits(0, 64) //we need to read data from memory
        io.dmem_req_addr := next_element_addr
        when(dmem_req_rdy_reg && io.dmem_req_val){ //if the memory is ready
          //common changes
          not_send_memory_matrix_positions(send_memory_matrix_position) := Bool(false)
          send_memory_matrix_position := PriorityEncoder(not_send_memory_matrix_positions)
          //different changes      
          when(rocc_funct_reg === UInt(1)) { //row
            next_element_addr := matrix_start_addr + (matrix_size*matrix_index + PriorityEncoder(not_send_memory_matrix_positions))*W
          }
          when(rocc_funct_reg === UInt(2)) { //column
            next_element_addr := matrix_start_addr + (matrix_index + PriorityEncoder(not_send_memory_matrix_positions)*matrix_size)*W
          }
        }
      }      
    }
    is(mem_write) {
      when(ask_memory_write){
        io.dmem_req_val := Bool(true)
        io.dmem_req_cmd := M_XWR //write data
        io.dmem_req_tag(4,0) := req_addr_reg
        when(accum_overflow_reg){
          io.dmem_req_data := UInt(overflow_dmem_data)
        }.otherwise{
          io.dmem_req_data := accum_resp_data_reg
        }
        
        io.dmem_req_addr := req_addr_reg //write back to the address
        when(dmem_req_rdy_reg && dmem_resp_tag_reg === req_addr_reg){
          mem_state := mem_idle
          ask_memory_write := Bool(false) //write finish
        }
      }.otherwise{
        mem_state := mem_write
      }
    }
  }



  //submodules states
  switch(ctrl_state) {
    is(ctrl_idle) {
      io.scache_resp_rdy := Bool(false) //not write until we ask
      when(mem_state === mem_read && not_send_submodules_matrix_positions(matrix_size - 1, 0).reduce(_||_) && dmem_resp_val_reg){ //if Accelerator has asked for any data and hasn't send all data to submodules, also received memory's valid signal, coming new data
        ask_memory_read := Bool(true) //the next time we can receive the data from SCacheModule, then we have to ask memory for data
        ctrl_state := ctrl_send
      }.elsewhen(!not_send_submodules_matrix_positions(matrix_size - 1, 0).reduce(_||_)){ //if we have send all the data to submodules, hence can get the final results
        ctrl_state := ctrl_wait_resp
      }.otherwise{
        ctrl_state := ctrl_idle
      }
    }
    is(ctrl_send) {
      io.scache_resp_rdy := Bool(false)//not write until we ask
      when(scache_req_val_reg){//ask SCacheModule for particular sum
        scache_req_val_reg := Bool(false)//then next time we don't need do that
        io.accum_req_data := scache_resp_data_reg
      }.elsewhen(dmem_resp_val_reg && dmem_resp_tag_reg =/= req_addr_reg){ //else, we need wait for the data from memory. So if Accelerator receives memory's valid signal, and the tag isn't the write address
        not_receive_matrix_positions(dmem_resp_tag_reg) := Bool(false)
        io.accum_req_data := dmem_resp_data_reg
        io.scache_req_data := dmem_resp_data_reg
        io.scache_index := dmem_resp_tag_reg
        io.scache_resp_rdy := Bool(true) //ask SCacheModule for particular sum
        //io.scache_req_row_or_col, we tell this information at Begin
      }
      when(!not_send_submodules_matrix_positions(matrix_size - 1,0).reduce(_||_)){ //if we have send all the data to submodules, hence can get the final results
        ctrl_state := ctrl_wait_resp
      }.otherwise{
        ctrl_state := ctrl_send
      }
    }
    is(ctrl_wait_resp) {
      io.scache_resp_rdy := Bool(false)//not write until we ask
      ask_memory_write := Bool(true) //then start to write back
      when(mem_state === mem_write && dmem_req_rdy_reg){
        ctrl_state := ctrl_idle
      }.otherwise{
        ctrl_state := ctrl_wait_resp
      }
    }
  }
}

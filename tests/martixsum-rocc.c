// This file is a RISC-V program to test the functionality
// of the matrixsum accelerator.
#include <stdio.h>
#include <stdint.h>
#include <assert.h>
#define MAXIUMSIZE 31
#define DATAWIDTH 64

#define func_MATRIX_SETUP 0
#define func_MATRIX_ROWSUM 1
#define func_MATRIX_COLSUM 2
#define c_matrixsize 20
#define XCUSTOM_MATRIX 0

static inline void matrixsum_setup(uint64_t *startaddr, int matrixsize) 
{ //opcode rs1  rs2  funct
  asm volatile ("custom0 x0, %[asm_startaddr], %[asm_matrixsize], func_MATRIX_SETUP" : : [asm_startaddr]"r"(startaddr), [asm_matrixsize]"r"(matrixsize));//sp13, lab4, CS250
  //ROCC_INSTRUCTION_SS(XCUSTOM_MATRIX, startaddr, matrixsize, func_MATRIX_SETUP);
}

static inline void matrixsum_rowsum(uint64_t *writeaddr, int idx)
{ //opcode rd rs1
  asm volatile ("custom0 %[asm_writeaddr], %[asm_writeaddr], x0, func_MATRIX_ROWSUM" : : [asm_writeaddr]"r"(writeaddr), [asm_idx]"r"(idx));//sp13, lab4, CS250
  //ROCC_INSTRUCTION_DS(XCUSTOM_MATRIX, writeaddr, idx, func_MATRIX_ROWSUM);
}

static inline void matrixsum_colsum(uint64_t *writeaddr, int idx)
{ //opcode rd rs1
  asm volatile ("custom0 %[asm_writeaddr], %[asm_writeaddr], x0, func_MATRIX_COLSUM" : : [asm_writeaddr]"r"(writeaddr), [asm_idx]"r"(idx));//sp13, lab4, CS250
  //ROCC_INSTRUCTION_DS(XCUSTOM_MATRIX, writeaddr, idx, func_MATRIX_COLSUM);
}

int main()
{
  //unsigned long start, end;
  printf("Start basic test 1.\n");
  uint64_t c_matrix[c_matrixsize][c_matrixsize]; //initialize a input matrix, whose each element equals to the sum of the row index and column index
  uint64_t c_row_sum[c_matrixsize];
  uint64_t c_col_sum[c_matrixsize];
  uint64_t c_row_sum_result[c_matrixsize];
  uint64_t c_col_sum_result[c_matrixsize];
  for (int i = 0; i < c_matrixsize; ++i)
  {
    c_row_sum[i] = 0;
    c_col_sum[i] = 0;
    c_row_sum_result[i] = 0;
    c_col_sum_result[i] = 0;
    for (int j = 0; j < c_matrixsize; ++i)
    {
      c_matrix[i][j] = i + j;
      c_row_sum_result[i] += + i + j
      c_col_sum_result[j] += + i + j
    }
  }
  asm volatile ("fence");//before the first instruction to ensure all previous memory access will complete before executing subsequent instructions
  matrixsum_setup(&c_matrix[0][0], c_matrixsize);
  for (int i = 0; i < c_matrixsize; ++i)
  {
    matrixsum_colsum(&c_col_sum[i], i);
    asm volatile ("fence"); //These barriers prevent a compiler from reordering instructions, they do not prevent reordering by CPU.
  } //do the full column sum
  for (int i = 0; i < c_matrixsize; ++i)
  {
    matrixsum_rowsum(&c_row_sum[i], i);
    asm volatile ("fence"); //These barriers prevent a compiler from reordering instructions, they do not prevent reordering by CPU.
  } //then do the full row sum

  for (int i = 0; i < c_matrixsize; i++)
  {
    printf("c_row_sum[%d]:%llu ==? c_row_sum_result[%d]:%llu \n",i,c_row_sum[i],i,c_row_sum_result[i]);
    printf("c_col_sum[%d]:%llu ==? c_col_sum_result[%d]:%llu \n",i,c_col_sum[i],i,c_col_sum_result[i]);
    assert(c_row_sum[i] != c_row_sum_result[i]);
    assert(c_col_sum[i] != c_col_sum_result[i]);
  }
  printf("Success!\n");
  return 0;
}

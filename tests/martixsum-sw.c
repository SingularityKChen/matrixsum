// This file is a RISC-V program to test the functionality
// of matrix sum without the matrixsum accelerator.
// That's means this is a software implementation.
// Only be tested via online debugger and compiler 
// url="https://www.onlinegdb.com/ "
// TODO: Count the running time or access times to memory;
#include <stdio.h>
#include <stdint.h>
#include <assert.h>
#define MAXIUMSIZE 31
#define DATAWIDTH 64

static const int c_matrixsize = 20;

typedef struct {
  unsigned int struct_matrixsize;//the matrixsize from instructions
  unsigned long *struct_startaddr;//the start address from instructions
} matrix_arguments;

void matrixsum_setup(matrix_arguments *marguments, uint64_t *startaddr, int matrixsize) 
{ //opcode rs1  rs2  funct
  marguments->struct_matrixsize = matrixsize;
  marguments->struct_startaddr = startaddr;
  printf("the startaddr is %llu, the matrixsize is %d\n", startaddr, matrixsize);
}

void matrixsum_rowsum(matrix_arguments *marguments, uint64_t *writeaddr, int idx)
{ //opcode rd rs1
  uint64_t tmp_sum = 0;
  uint64_t *tmp_addr = 0;
  for (int i = 0; i < marguments->struct_matrixsize; ++i)
  {
    tmp_addr = marguments->struct_startaddr + i + marguments->struct_matrixsize*idx; //calculate the address of i-th element in current row 
    printf("%d-th tmp_addr equals to %x and its values is %llu \n", i, tmp_addr, *tmp_addr);
    tmp_sum += *tmp_addr;
  }
  *writeaddr = tmp_sum; //write back the result
  printf("calculated %d-th row's sum, the result is %llu \n",idx, *writeaddr);
}

void matrixsum_colsum(matrix_arguments *marguments, uint64_t *writeaddr, int idx)
{ //opcode rd rs1
  uint64_t tmp_sum = 0;
  uint64_t *tmp_addr = 0;
  for (int i = 0; i < marguments->struct_matrixsize; ++i)
  {
    tmp_addr = marguments->struct_startaddr + i*marguments->struct_matrixsize + idx; //calculate the address of i-th element in current column 
    printf("%d-th tmp_addr equals to %x and its values is %llu \n", i, tmp_addr, *tmp_addr);
    tmp_sum += *tmp_addr;
  }
  *writeaddr = tmp_sum; //write back the result
  printf("calculated %d-th column's sum, the result is %llu \n",idx, *writeaddr);
}

int main()
{
  printf("Start basic test 1.\n");
  uint64_t c_matrix[c_matrixsize][c_matrixsize]; //initialize a input matrix, whose each element equals to the sum of the row index and column index, 64-bit
  uint64_t c_row_sum[c_matrixsize]; //stores the row sums
  uint64_t c_col_sum[c_matrixsize]; //stores the column sums
  uint64_t c_row_sum_result[c_matrixsize]; //golden module of row sums
  uint64_t c_col_sum_result[c_matrixsize]; ////golden module of column sums
  for (int i = 0; i < c_matrixsize; ++i) //initialize these arraies
  {
    c_row_sum[i] = 0;
    c_col_sum[i] = 0;
    c_row_sum_result[i] = 0;
    c_col_sum_result[i] = 0;
    for (int j = 0; j < c_matrixsize; ++j)
    {
      c_matrix[i][j] = 0;
    }
  }
  //generate the input sum and golden results
  for (int i = 0; i < c_matrixsize; ++i)//i equals to row
  {
    for (int j = 0; j < c_matrixsize; ++j)//j equals to column
    {
      c_matrix[i][j] = i + j;
      c_row_sum_result[i] += + i + j;
      c_col_sum_result[j] += + i + j;
    }
  }
  matrix_arguments marguments;
  matrixsum_setup(&marguments, &c_matrix[0][0], c_matrixsize);
  for (int i = 0; i < c_matrixsize; ++i)
  {
    printf("c_matrix[0][%d] equals to %llu and its address is %x \n", i, c_matrix[0][i], &c_matrix[0][i]);
    printf("c_matrix[1][%d] equals to %llu and its address is %x \n", i, c_matrix[1][i], &c_matrix[1][i]);
    printf("the column's difference is %d\n", &c_matrix[1][i] - &c_matrix[0][i]);
    matrixsum_colsum(&marguments, &c_col_sum[i], i);
  } //do the full column sum
  for (int i = 0; i < c_matrixsize; ++i)
  {
    printf("c_matrix[%d][0] equals to %llu and its address is %x \n", i, c_matrix[i][0], &c_matrix[i][0]);
    printf("c_matrix[%d][1] equals to %llu and its address is %x \n", i, c_matrix[i][1], &c_matrix[i][1]);
    printf("the row's difference is %d\n", &c_matrix[i][1] - &c_matrix[i][0]);
    matrixsum_rowsum(&marguments, &c_row_sum[i], i);
  } //then do the full row sum

  for (int i = 0; i < c_matrixsize; i++)
  {
    printf("c_row_sum[%d]:%llu ==? c_row_sum_result[%d]:%llu \n",i,c_row_sum[i],i,c_row_sum_result[i]);
    printf("c_col_sum[%d]:%llu ==? c_col_sum_result[%d]:%llu \n",i,c_col_sum[i],i,c_col_sum_result[i]);
    assert(c_row_sum[i] == c_row_sum_result[i]);
    assert(c_col_sum[i] == c_col_sum_result[i]);
  }
  printf("Success!\n");
  return 0;
}

#include <stdint.h>
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
}

void matrixsum_rowsum(matrix_arguments *marguments, uint64_t *writeaddr, int idx)
{ //opcode rd rs1
  uint64_t tmp_sum = 0;
  uint64_t *tmp_addr = 0;
  for (int i = 0; i < marguments->struct_matrixsize; ++i)
  {
    tmp_addr = marguments->struct_startaddr + i + marguments->struct_matrixsize*idx; //calculate the address of i-th element in current row 
    tmp_sum += *tmp_addr;
  }
  *writeaddr = tmp_sum; //write back the result
}

void matrixsum_colsum(matrix_arguments *marguments, uint64_t *writeaddr, int idx)
{ //opcode rd rs1
  uint64_t tmp_sum = 0;
  uint64_t *tmp_addr = 0;
  for (int i = 0; i < marguments->struct_matrixsize; ++i)
  {
    //tmp_addr = marguments->struct_startaddr + (i*marguments->struct_matrixsize + idx)*DATAWIDTH;
    tmp_addr = marguments->struct_startaddr + i*marguments->struct_matrixsize + idx; //calculate the address of i-th element in current column 
    tmp_sum += *tmp_addr;
  }
  *writeaddr = tmp_sum; //write back the result
}

int main()
{
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
    matrixsum_colsum(&marguments, &c_col_sum[i], i);
  } //do the full column sum
  for (int i = 0; i < c_matrixsize; ++i)
  {
    matrixsum_rowsum(&marguments, &c_row_sum[i], i);
  } //then do the full row sum
}

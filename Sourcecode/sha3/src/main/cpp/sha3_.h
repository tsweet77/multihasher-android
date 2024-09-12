#pragma once

# define KECCAK1600_WIDTH 1600
# define SHA3_MDSIZE(bitlen)    (bitlen / 8)
# define KMAC_MDSIZE(bitlen)    2 * (bitlen / 8)
# define SHA3_BLOCKSIZE(bitlen) (KECCAK1600_WIDTH - bitlen * 2) / 8

typedef struct keccak_st KECCAK1600_CTX;

typedef size_t(sha3_absorb_fn)(void* vctx, const void* in, size_t inlen);
typedef int (sha3_final_fn)(void* vctx, unsigned char* out, size_t outlen);
typedef int (sha3_squeeze_fn)(void* vctx, unsigned char* out, size_t outlen);

typedef struct prov_sha3_meth_st
{
    sha3_absorb_fn* absorb;
    sha3_final_fn* final;
    sha3_squeeze_fn* squeeze;
} PROV_SHA3_METHOD;

#define XOF_STATE_INIT    0
#define XOF_STATE_ABSORB  1
#define XOF_STATE_FINAL   2
#define XOF_STATE_SQUEEZE 3

struct keccak_st {
    uint64_t A[5][5];
    unsigned char buf[KECCAK1600_WIDTH / 8 - 32];
    size_t block_size;          /* cached ctx->digest->block_size */
    size_t md_size;             /* output length, variable in XOF */
    size_t bufsz;               /* used bytes in below buffer */
    unsigned char pad;
    PROV_SHA3_METHOD meth;
    int xof_state;
};

int ossl_sha3_init(KECCAK1600_CTX* ctx, unsigned char pad, size_t bitlen);
int ossl_sha3_update(KECCAK1600_CTX* ctx, const void* _inp, size_t len);
int ossl_sha3_final(KECCAK1600_CTX* ctx, unsigned char* out, size_t outlen);
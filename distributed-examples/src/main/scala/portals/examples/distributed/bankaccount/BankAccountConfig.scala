package portals.examples.distributed.bankaccount

object BankAccountConfig:
  inline val N_EVENTS = 128
  inline val N_ACCOUNTS = 128
  inline val N_OPS_PER_SAGA = 4
  inline val LOGGING = false

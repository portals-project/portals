package pods.workflows

type WithContext[T, U, S] = OperatorCtx[T, U] ?=> S

package pods.workflows

/** Contextual function type for OperatorCtx.
  * 
  * Used to synthesize the contextual parameters. For example, by wrapping
  * the return value S of a function, then function body is syntesized to 
  * have a contextual parameter of type OperatorCtx[T, U].
  * 
  * Consider the following function.
  * 
  *   def f(x: S): S = x
  * 
  * In order to access some OperatorCtx from within the function, we would need
  * to pass it as some parameter. Doing this through implicit parameters is
  * convenient, which is exactly what we achieve by the following.
  * 
  *   def f[T, U](x: S): WithContext[T, U, S] = 
  *     summon[OperatorCtx[T, U]] // summon and use the OperatorCtx
  *     x // return x of type S
  * 
  * @tparam S return type of the function
  * @tparam U type of the OperatorCtx[_, U]
  * @tparam T type of the OperatorCtx[T, _]
 */
type WithContext[T, U, S] = OperatorCtx[T, U] ?=> S

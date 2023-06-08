var builder = PortalsJS.ApplicationBuilder("ArrayArray")
let arrayarray = [[1, 2], [3, 4]];
let fromArrayOfArraysResult = builder.generators.fromArrayOfArrays(arrayarray);
var _ = builder.workflows
    .source(fromArrayOfArraysResult.stream)
    .logger()
    .sink()
    .freeze()
var arar = builder.build()
var system = PortalsJS.System()
system.launch(arar)
system.stepUntilComplete()

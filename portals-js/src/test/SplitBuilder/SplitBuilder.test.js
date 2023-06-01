// Invoke ApplicationBuilder directly
let applicationBuilder = PortalsJS.ApplicationBuilder('SplitBuilder');

// Assuming that splits is a function that returns an instance of SplitBuilderJS
let splitBuilder = applicationBuilder.splits;

// Assuming that we have an AtomicSplitterRefKind object and a Function1JS object
let splitter = applicationBuilder.generators.fromRange(0, 128, 8).stream; // AtomicSplitterRefKind instance
let f = function(x) { return x > 5; }; // Function1JS instance

// Test split method
try {
    let splitResult = splitBuilder.split(splitter, f);
    console.log('split method executed successfully. Result:', splitResult);
} catch (error) {
    console.error('Error when executing split method:', error);
}

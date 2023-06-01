// Invoke ApplicationBuilder directly
let applicationBuilder = PortalsJS.ApplicationBuilder('Splitter');

// Assuming that splitters is a function that returns an instance of SplitterBuilderJS
let splitterBuilder = applicationBuilder.splitters;

// Assuming that we have an AtomicStreamRefKind object
let stream = applicationBuilder.generators.fromRange(0, 128, 8).stream; // AtomicStreamRefKind instance

// Test empty method
try {
    let emptyResult = splitterBuilder.empty(stream);
    console.log('empty method executed successfully. Result:', emptyResult);
} catch (error) {
    console.error('Error when executing empty method:', error);
}

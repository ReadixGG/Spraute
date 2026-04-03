(async () => {
  try {
    await import('./src/visual.js');
    console.log('OK');
  } catch (e) {
    console.error(e);
  }
})();
export class SupersededRequestError extends Error {
  constructor() { super('A newer refresh superseded this response'); this.name = 'SupersededRequestError'; }
}

/** Last-started response wins; serialize asynchronous cache commits as well as UI projection. */
export class LatestRequestCoordinator {
  private sequence = 0;
  private commitTail: Promise<void> = Promise.resolve();

  begin() {
    const sequence = ++this.sequence;
    const isCurrent = () => sequence === this.sequence;
    const assertCurrent = () => { if (!isCurrent()) throw new SupersededRequestError(); };
    return {
      isCurrent,
      assertCurrent,
      commit: <T>(operation: () => Promise<T>): Promise<T> => {
        const running = this.commitTail.then(async () => {
          assertCurrent();
          const result = await operation();
          assertCurrent();
          return result;
        });
        // A failed/superseded write must not poison the next request's commit queue.
        this.commitTail = running.then(() => undefined, () => undefined);
        return running;
      },
    };
  }
}

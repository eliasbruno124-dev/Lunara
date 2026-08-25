export class StaleIdentityError extends Error {
  constructor() {
    super('The server identity changed while the operation was running');
    this.name = 'StaleIdentityError';
  }
}

export type IdentityGuard = () => void;

/** Prevents asynchronous account-A work from committing after adopting B. */
export class IdentityEpochCoordinator {
  private generation = 0;
  private identityKey: string | null = null;
  private readonly active = new Set<Promise<unknown>>();
  private transitionTail: Promise<void> = Promise.resolve();
  private pendingTransitions = 0;

  async run<T>(identityKey: string, operation: (assertCurrent: IdentityGuard) => Promise<T>): Promise<T> {
    if (this.pendingTransitions > 0 || identityKey !== this.identityKey) {
      throw new StaleIdentityError();
    }
    const generation = this.generation;
    const assertCurrent = (): void => {
      if (
        generation !== this.generation ||
        identityKey !== this.identityKey ||
        this.pendingTransitions > 0
      ) throw new StaleIdentityError();
    };
    const running = (async () => {
      assertCurrent();
      const result = await operation(assertCurrent);
      assertCurrent();
      return result;
    })();
    this.active.add(running);
    try {
      return await running;
    } finally {
      this.active.delete(running);
    }
  }

  transition(nextIdentityKey: string | null, operation: () => Promise<void>): Promise<boolean> {
    const generation = ++this.generation;
    this.pendingTransitions += 1;
    const active = [...this.active];
    const transitioning = this.transitionTail.then(async () => {
      await Promise.allSettled(active);
      if (generation !== this.generation) return false;
      await operation();
      if (generation !== this.generation) return false;
      this.identityKey = nextIdentityKey;
      return true;
    });
    this.transitionTail = transitioning.then(() => undefined, () => undefined);
    return transitioning.finally(() => {
      this.pendingTransitions -= 1;
    });
  }

  transitionIfCurrent(
    expectedIdentityKey: string,
    nextIdentityKey: string | null,
    operation: () => Promise<void>,
  ): Promise<boolean> {
    if (this.pendingTransitions > 0 || this.identityKey !== expectedIdentityKey) {
      return Promise.resolve(false);
    }
    return this.transition(nextIdentityKey, operation);
  }
}

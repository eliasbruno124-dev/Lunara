import {
  buildMessage,
  ValidateBy,
  type ValidationOptions,
} from 'class-validator';

export const ACCOUNT_PASSWORD_MIN_LENGTH = 8;
export const ACCOUNT_PASSWORD_MAX_LENGTH = 128;

// Existing-password verification deliberately accepts any non-empty value up
// to the storage limit. This keeps login and account deletion compatible if
// the creation policy changes again later.
export const EXISTING_PASSWORD_MIN_LENGTH = 1;

export const passwordCodePointLength = (value: string): number => Array.from(value).length;

export const hasPasswordCodePointLength = (
  value: unknown,
  minimum: number,
  maximum: number,
): value is string =>
  typeof value === 'string' &&
  passwordCodePointLength(value) >= minimum &&
  passwordCodePointLength(value) <= maximum;

/**
 * JSON Schema/OpenAPI length is defined in Unicode code points. validator.js'
 * generic Length decorator additionally collapses variation selectors, which
 * made Android and the API disagree for otherwise valid passwords such as ❤️.
 */
export function IsPasswordCodePointLength(
  minimum: number,
  maximum: number,
  validationOptions?: ValidationOptions,
): PropertyDecorator {
  return ValidateBy(
    {
      name: 'isPasswordCodePointLength',
      validator: {
        validate: (value: unknown): boolean =>
          hasPasswordCodePointLength(value, minimum, maximum),
        defaultMessage: buildMessage(
          (eachPrefix) =>
            `${eachPrefix}$property must contain between ${minimum} and ${maximum} Unicode code points`,
          validationOptions,
        ),
      },
    },
    validationOptions,
  );
}

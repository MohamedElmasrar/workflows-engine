/** Types shared by every API area: pagination and the current user. */

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface Me {
  id: string;
  username: string;
  admin: boolean;
  roles: string[];
}

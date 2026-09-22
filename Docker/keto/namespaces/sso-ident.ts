import { Namespace, Context } from "@ory/keto-namespace-types"

class User implements Namespace {}

class Role implements Namespace {
  related: {
    members: User[]
  }

  permits = {
    isMember: (ctx: Context): boolean => this.related.members.includes(ctx.subject),
  }
}

class Service implements Namespace {
  related: {
    access: Role[]
  }

  permits = {
    access: (ctx: Context): boolean =>
      this.related.access.traverse((role) => role.permits.isMember(ctx)),
  }
}

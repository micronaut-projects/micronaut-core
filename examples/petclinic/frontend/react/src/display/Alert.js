import React from 'react'

const Alert = ({message, level}) => message ? <div className={`alert alert-${level ? level: 'info'} alert-dismissible fade show`} role="alert">
  {message}
  <button type="button" className="close" data-dismiss="alert" aria-label="Close"><span aria-hidden="true">×</span>
  </button>
</div> : null

export default Alert;
import React from 'react'

const Error = ({message}) => message ? <div className="alert alert-warning alert-dismissible fade show" role="alert">
  {message}
  <button type="button" className="close" data-dismiss="alert" aria-label="Close"><span aria-hidden="true">×</span>
  </button>
</div> : null

export default Error;
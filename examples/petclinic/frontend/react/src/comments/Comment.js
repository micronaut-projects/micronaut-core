import React from 'react'
import {shape, string, bool, func} from 'prop-types'

const Comment = ({comment, isThread, expand, close, expanded}) => <div className="card-body">
    <div className='row'>
      <div className='col-sm-10'>
        <h6 className="card-title"><b>{comment.poster}</b> said:</h6>
        <p className="card-text">{comment.content}</p>
      </div>
      <div className='col-sm-2'>
        {isThread && !expanded ? <button className='btn btn-success comment-btn' onClick={expand}>+</button> :
          isThread && expanded ? <button className='btn btn-danger comment-btn' onClick={close}>-</button> : null}
      </div>
    </div>
  </div>

Comment.propTypes = {
  comment: shape({
    poster: string,
    content: string
  }),
  isThread: bool,
  expand: func,
  close: func,
  expanded: bool
}


export default Comment;